package ee.taltech.ite4120.vessels.dto;

import ee.taltech.ite4120.vessels.model.License;
import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import java.time.LocalDate;

/**
 * A license as the API shows it: the stored facts plus the derived
 * {@code effectiveStatus}. Validity is never stored — it is computed here from
 * the dates, the stored status and today's date (VESSELS.01 § Derived Effective Status).
 */
public record LicenseView(
        Long id,
        Long vesselId,
        String licenseNumber,
        String licenseType,
        LocalDate issuedOn,
        LocalDate validUntil,
        String status,
        LocalDate revokedOn,
        String revocationReason,
        LicenseEffectiveStatus effectiveStatus) {

    public static LicenseView of(License l, LocalDate today) {
        return new LicenseView(l.getId(), l.getVesselId(), l.getLicenseNumber(), l.getLicenseType(),
                l.getIssuedOn(), l.getValidUntil(), l.getStatus(), l.getRevokedOn(), l.getRevocationReason(),
                LicenseEffectiveStatus.derive(l.getStatus(), l.getIssuedOn(), l.getValidUntil(), today));
    }
}
