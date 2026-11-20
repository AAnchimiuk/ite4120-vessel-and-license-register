package ee.taltech.ite4120.vessels.model;

import java.time.LocalDate;

/**
 * Derived dynamic validity status for an operational license.
 * Evaluated top-down against a reference date (today in Europe/Tallinn).
 */
public enum LicenseEffectiveStatus {

    REVOKED,
    NOT_YET_VALID,
    EXPIRED,
    EXPIRING_SOON,
    VALID;

    /**
     * Derives the effective status following the priority order in VESSELS.01.
     */
    public static LicenseEffectiveStatus derive(String storedStatus, LocalDate issuedOn, LocalDate validUntil, LocalDate today) {
        if ("REVOKED".equalsIgnoreCase(storedStatus)) {
            return REVOKED;
        }
        if (issuedOn != null && issuedOn.isAfter(today)) {
            return NOT_YET_VALID;
        }
        if (validUntil != null && validUntil.isBefore(today)) {
            return EXPIRED;
        }
        if (validUntil != null && !validUntil.isAfter(today.plusDays(30))) {
            return EXPIRING_SOON;
        }
        return VALID;
    }

    /**
     * Both VALID and EXPIRING_SOON count as currently valid.
     */
    public boolean isCurrentlyValid() {
        return this == VALID || this == EXPIRING_SOON;
    }
}
