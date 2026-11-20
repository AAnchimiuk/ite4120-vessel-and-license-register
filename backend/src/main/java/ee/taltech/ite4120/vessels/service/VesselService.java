package ee.taltech.ite4120.vessels.service;

import ee.taltech.ite4120.animals.ownerregistry.OwnerInfo;
import ee.taltech.ite4120.animals.ownerregistry.OwnerRegistryAdapter;
import ee.taltech.ite4120.vessels.dto.VesselQueryParams;
import ee.taltech.ite4120.vessels.model.RetirementReason;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselOwnership;
import ee.taltech.ite4120.vessels.model.VesselType;
import ee.taltech.ite4120.vessels.repository.LicenseRepository;
import ee.taltech.ite4120.vessels.repository.VesselOwnershipRepository;
import ee.taltech.ite4120.vessels.repository.VesselRepository;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for the vessel register — VESSELS.01 § Business tests 1, 2, 13–16
 * plus the owner lookup. Controllers stay HTTP-only; every rule lives here.
 */
@Service
public class VesselService {

    public static final String RETIREMENT_REVOCATION_REASON = "Vessel retired";

    private final VesselRepository vessels;
    private final VesselOwnershipRepository ownerships;
    private final LicenseRepository licenses;
    private final OwnerRegistryAdapter ownerRegistry;

    public VesselService(VesselRepository vessels, VesselOwnershipRepository ownerships,
            LicenseRepository licenses, OwnerRegistryAdapter ownerRegistry) {
        this.vessels = vessels;
        this.ownerships = ownerships;
        this.licenses = licenses;
        this.ownerRegistry = ownerRegistry;
    }

    public QueryResult<Vessel> search(VesselQueryParams params) {
        return vessels.search(params);
    }

    /** A vessel by id — active OR retired (retired vessels stay readable). */
    public Vessel require(Long id) {
        Vessel v = vessels.load(id);
        if (v == null) {
            throw new NotFoundException("vessel " + id + " not found");
        }
        return v;
    }

    /** A vessel by id that may still be changed: 404 if unknown, 409 if retired. */
    public Vessel requireActive(Long id) {
        Vessel v = require(id);
        if (isRetired(v)) {
            throw new ConflictException("vessel", "vessel " + id + " is retired and can no longer be changed");
        }
        return v;
    }

    @Transactional
    public Vessel create(Vessel vessel) {
        validate(vessel);
        if (vessels.loadActiveByRegistryCode(vessel.getRegistryCode()) != null) {
            throw new ConflictException("vessel",
                    "an active vessel with registry code " + vessel.getRegistryCode() + " already exists");
        }
        Long id = vessels.insert(vessel);
        if (vessel.getOwnerIsikukood() != null) {
            openFirstOwnership(id, vessel.getOwnerIsikukood());
        }
        return require(id);
    }

    @Transactional
    public Vessel update(Long id, Vessel vessel) {
        Vessel existing = requireActive(id);
        // Identity does not change after registration.
        vessel.setRegistryCode(existing.getRegistryCode());
        validate(vessel);

        String currentOwner = existing.getOwnerIsikukood();
        String requested = vessel.getOwnerIsikukood();
        if (currentOwner != null) {
            if (requested != null && !requested.equals(currentOwner)) {
                throw new ApiClientException(
                        "the owner can only be changed by an ownership transfer — use POST /api/vessels/"
                                + id + "/ownership");
            }
            vessel.setOwnerIsikukood(currentOwner);
        }
        vessel.setId(id);
        if (!vessels.update(vessel)) {
            throw new NotFoundException("vessel " + id + " not found");
        }
        if (currentOwner == null && requested != null) {
            openFirstOwnership(id, requested);
        }
        return require(id);
    }

    /**
     * Retires a vessel: records date, reason and note, soft-deletes the row (which frees
     * the registry code) and revokes every license still valid on that date — one transaction.
     */
    @Transactional
    public Vessel retire(Long id, LocalDate retiredOn, String reason, String note) {
        Vessel v = require(id);
        if (isRetired(v)) {
            throw new ConflictException("vessel", "vessel " + id + " is already retired");
        }
        if (retiredOn == null) {
            throw new ApiClientException("retiredOn is required");
        }
        if (retiredOn.isAfter(VesselTime.today())) {
            throw new ApiClientException("retiredOn must not be in the future");
        }
        if (reason == null || !vessels.retirementReasonExists(reason)) {
            throw new ApiClientException("unknown retirement reason: " + reason);
        }
        if ("OTHER".equals(reason) && (note == null || note.isBlank())) {
            throw new ApiClientException("retirementNote is required when reason is OTHER");
        }
        licenses.revokeAllValidForVessel(id, retiredOn, RETIREMENT_REVOCATION_REASON, retiredOn);
        if (!vessels.retire(id, retiredOn, reason, note)) {
            throw new ConflictException("vessel", "vessel " + id + " is already retired");
        }
        return require(id);
    }

    /**
     * The current owner's name and address from the population registry (mock locally,
     * X-Road in production). A registry failure surfaces as OwnerRegistryException → 502
     * on this endpoint only — the vessel itself never depends on it (fail open).
     */
    public OwnerInfo owner(Long id) {
        Vessel v = require(id);
        if (v.getOwnerIsikukood() == null) {
            throw new NotFoundException("vessel " + id + " has no owner");
        }
        return lookupOrNotFound(v.getOwnerIsikukood());
    }

    OwnerInfo lookupOrNotFound(String isikukood) {
        OwnerInfo info = ownerRegistry.lookup(isikukood);
        if (info == null) {
            throw new NotFoundException("person " + isikukood + " not found in the population registry");
        }
        return info;
    }

    public List<VesselType> types() {
        return vessels.listVesselTypes();
    }

    public List<RetirementReason> retirementReasons() {
        return vessels.listRetirementReasons();
    }

    private void openFirstOwnership(Long vesselId, String isikukood) {
        VesselOwnership o = new VesselOwnership();
        o.setVesselId(vesselId);
        o.setOwnerIsikukood(isikukood);
        o.setOwnedFrom(VesselTime.today());
        ownerships.insert(o);
    }

    private void validate(Vessel v) {
        if (v.getBuildYear() != null && v.getBuildYear() > VesselTime.today().getYear()) {
            throw new ApiClientException("buildYear must not be in the future");
        }
        if (v.getTypeCode() == null || !vessels.vesselTypeExists(v.getTypeCode())) {
            throw new ApiClientException("unknown typeCode: " + v.getTypeCode());
        }
    }

    private static boolean isRetired(Vessel v) {
        return !"A".equals(v.getSysStatus());
    }
}
