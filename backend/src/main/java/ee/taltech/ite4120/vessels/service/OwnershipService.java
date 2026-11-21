package ee.taltech.ite4120.vessels.service;

import ee.taltech.ite4120.animals.ownerregistry.OwnerInfo;
import ee.taltech.ite4120.animals.ownerregistry.OwnerRegistryAdapter;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselOwnership;
import ee.taltech.ite4120.vessels.repository.VesselOwnershipRepository;
import ee.taltech.ite4120.vessels.repository.VesselRepository;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ownership history and transfer — VESSELS.01 § Business tests 9–12.
 *
 * <p>A transfer FAILS CLOSED: the new owner's code is confirmed with the population
 * registry (mock X-Road locally) BEFORE anything is written; if the registry is
 * down the {@code OwnerRegistryException} propagates (502) and nothing is saved.
 * The registry call happens outside the database transaction.
 */
@Service
public class OwnershipService {

    private final VesselOwnershipRepository ownerships;
    private final VesselRepository vessels;
    private final VesselService vesselService;
    private final OwnerRegistryAdapter ownerRegistry;
    private final TransactionTemplate tx;

    public OwnershipService(VesselOwnershipRepository ownerships, VesselRepository vessels,
            VesselService vesselService, OwnerRegistryAdapter ownerRegistry, TransactionTemplate tx) {
        this.ownerships = ownerships;
        this.vessels = vessels;
        this.vesselService = vesselService;
        this.ownerRegistry = ownerRegistry;
        this.tx = tx;
    }

    /** Ownership history, newest first. Retired vessels included. */
    public List<VesselOwnership> history(Long vesselId) {
        vesselService.require(vesselId);
        return ownerships.listHistoryByVesselId(vesselId);
    }

    public VesselOwnership transfer(Long vesselId, String newOwnerIsikukood, LocalDate transferDate) {
        Vessel vessel = vesselService.requireActive(vesselId);

        if (newOwnerIsikukood == null || !newOwnerIsikukood.matches("\\d{11}")) {
            throw new ApiClientException("newOwnerIsikukood must be exactly 11 digits");
        }
        if (newOwnerIsikukood.equals(vessel.getOwnerIsikukood())) {
            throw new ApiClientException("new owner must be different from current owner");
        }
        if (transferDate == null) {
            throw new ApiClientException("transferDate is required");
        }
        if (transferDate.isAfter(VesselTime.today())) {
            throw new ApiClientException("transferDate must not be in the future");
        }
        VesselOwnership current = ownerships.loadCurrentByVesselId(vesselId);
        if (current != null && transferDate.isBefore(current.getOwnedFrom())) {
            throw new ApiClientException("transferDate must not be before the current ownership started ("
                    + current.getOwnedFrom() + ")");
        }

        // Fail closed: ask the registry first, outside any DB transaction.
        OwnerInfo person = ownerRegistry.lookup(newOwnerIsikukood);
        if (person == null) {
            throw new ApiClientException("person " + newOwnerIsikukood + " not found in the population registry");
        }

        Long newId = tx.execute(status -> {
            if (current != null && !ownerships.closeCurrentPeriod(vesselId, transferDate)) {
                throw new ApiClientException("the ownership changed concurrently — retry");
            }
            VesselOwnership o = new VesselOwnership();
            o.setVesselId(vesselId);
            o.setOwnerIsikukood(newOwnerIsikukood);
            o.setOwnedFrom(transferDate);
            Long id = ownerships.insert(o);
            vessels.updateOwnerIsikukood(vesselId, newOwnerIsikukood);
            return id;
        });
        return ownerships.load(newId);
    }

    /** Name and address of the owner of one historical period (fail open: 502 only here). */
    public OwnerInfo ownerOfPeriod(Long vesselId, Long ownershipId) {
        vesselService.require(vesselId);
        VesselOwnership o = ownerships.load(ownershipId);
        if (o == null || !vesselId.equals(o.getVesselId())) {
            throw new NotFoundException("ownership " + ownershipId + " not found on vessel " + vesselId);
        }
        return vesselService.lookupOrNotFound(o.getOwnerIsikukood());
    }
}
