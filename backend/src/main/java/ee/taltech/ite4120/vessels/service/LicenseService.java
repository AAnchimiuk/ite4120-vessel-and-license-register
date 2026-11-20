package ee.taltech.ite4120.vessels.service;

import ee.taltech.ite4120.vessels.dto.ExpiringLicenseQueryParams;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseView;
import ee.taltech.ite4120.vessels.dto.LicenseType;
import ee.taltech.ite4120.vessels.dto.LicenseView;
import ee.taltech.ite4120.vessels.model.License;
import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import ee.taltech.ite4120.vessels.repository.LicenseRepository;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** License rules — VESSELS.01 § Business tests 3–8. Validity is derived, never stored. */
@Service
public class LicenseService {

    private static final List<LicenseType> TYPES = List.of(
            new LicenseType("NAVIGATION", "Navigation permit"),
            new LicenseType("FISHING", "Commercial fishing license"),
            new LicenseType("PASSENGER", "Passenger transport certification"),
            new LicenseType("CARGO", "Cargo transport license"),
            new LicenseType("SAFETY", "Safety certificate"));

    private final LicenseRepository licenses;
    private final VesselService vessels;

    public LicenseService(LicenseRepository licenses, VesselService vessels) {
        this.licenses = licenses;
        this.vessels = vessels;
    }

    /** All licenses of a vessel (its full history), newest issued first. Retired vessels included. */
    public List<LicenseView> list(Long vesselId) {
        vessels.require(vesselId);
        LocalDate today = VesselTime.today();
        return licenses.listByVesselId(vesselId).stream().map(l -> LicenseView.of(l, today)).toList();
    }

    public LicenseView get(Long vesselId, Long licenseId) {
        vessels.require(vesselId);
        return LicenseView.of(requireOf(vesselId, licenseId), VesselTime.today());
    }

    @Transactional
    public LicenseView add(Long vesselId, License license) {
        vessels.requireActive(vesselId);
        if (license.getLicenseNumber() == null || license.getLicenseNumber().isBlank()
                || license.getLicenseType() == null || license.getLicenseType().isBlank()
                || license.getIssuedOn() == null || license.getValidUntil() == null) {
            throw new ApiClientException("licenseNumber, licenseType, issuedOn and validUntil are required");
        }
        if (license.getValidUntil().isBefore(license.getIssuedOn())) {
            throw new ApiClientException("validUntil must not precede issuedOn");
        }
        license.setVesselId(vesselId);
        license.setStatus("ACTIVE");
        Long id = licenses.insert(license);
        return LicenseView.of(licenses.load(id), VesselTime.today());
    }

    @Transactional
    public LicenseView revoke(Long vesselId, Long licenseId, LocalDate revokedOn, String reason) {
        vessels.requireActive(vesselId);
        License l = requireOf(vesselId, licenseId);
        LocalDate today = VesselTime.today();

        if (reason == null || reason.isBlank()) {
            throw new ApiClientException("reason must not be blank");
        }
        if (revokedOn == null) {
            throw new ApiClientException("revokedOn is required");
        }
        if (revokedOn.isAfter(today)) {
            throw new ApiClientException("revokedOn must not be in the future");
        }
        if (revokedOn.isBefore(l.getIssuedOn())) {
            throw new ApiClientException("revokedOn must not precede issuedOn");
        }
        LicenseEffectiveStatus status =
                LicenseEffectiveStatus.derive(l.getStatus(), l.getIssuedOn(), l.getValidUntil(), today);
        if (status == LicenseEffectiveStatus.REVOKED) {
            throw new ConflictException("license", "license is already revoked");
        }
        if (!status.isCurrentlyValid()) {
            throw new ConflictException("license", "license is not currently valid (" + status + ")");
        }
        if (!licenses.revoke(licenseId, revokedOn, reason.trim())) {
            throw new ConflictException("license", "license is already revoked");
        }
        return LicenseView.of(licenses.load(licenseId), today);
    }

    /**
     * Soft-deletes a license that is {@code NOT_YET_VALID} ({@code issued_on > today}).
     * Licenses that have already entered into force, expired, or been revoked belong
     * to the permanent audit trail and cannot be deleted (VESSELS.01 § Business test 17).
     */
    @Transactional
    public void delete(Long vesselId, Long licenseId) {
        vessels.requireActive(vesselId);
        License l = requireOf(vesselId, licenseId);
        LocalDate today = VesselTime.today();
        LicenseEffectiveStatus status =
                LicenseEffectiveStatus.derive(l.getStatus(), l.getIssuedOn(), l.getValidUntil(), today);
        if (status != LicenseEffectiveStatus.NOT_YET_VALID) {
            throw new ConflictException("license",
                    "only a license that is NOT_YET_VALID can be deleted (current status: " + status + ")");
        }
        if (!licenses.delete(licenseId)) {
            throw new NotFoundException("license " + licenseId + " not found on vessel " + vesselId);
        }
    }

    /** Licenses expiring within a window, soonest first, active vessels only. */
    public QueryResult<ExpiringLicenseView> expiring(ExpiringLicenseQueryParams params) {
        Integer days = params.getWithinDays();
        if (days == null) {
            params.setWithinDays(30);
        } else if (days < 1 || days > 365) {
            throw new ApiClientException("withinDays must be between 1 and 365");
        }
        return licenses.searchExpiring(params, VesselTime.today());
    }

    public List<LicenseType> types() {
        return TYPES;
    }

    private License requireOf(Long vesselId, Long licenseId) {
        License l = licenses.load(licenseId);
        if (l == null || !vesselId.equals(l.getVesselId())) {
            throw new NotFoundException("license " + licenseId + " not found on vessel " + vesselId);
        }
        return l;
    }
}
