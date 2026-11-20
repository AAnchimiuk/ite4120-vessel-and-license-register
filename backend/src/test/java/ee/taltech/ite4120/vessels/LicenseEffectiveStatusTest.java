package ee.taltech.ite4120.vessels;

import static org.assertj.core.api.Assertions.assertThat;

import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Pure unit test (no database) of the derived license status — the table in
 * docs/src/specifications/VESSELS.01 § Derived Effective Status for Licenses,
 * including the boundary days.
 */
class LicenseEffectiveStatusTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    private static LicenseEffectiveStatus derive(String status, LocalDate issued, LocalDate until) {
        return LicenseEffectiveStatus.derive(status, issued, until, TODAY);
    }

    @Test
    void followsTheDatesFromTheSpecificationExample() {
        LocalDate past = TODAY.minusYears(1);
        assertThat(derive("ACTIVE", past, LocalDate.of(2026, 11, 5))).isEqualTo(LicenseEffectiveStatus.EXPIRED);
        assertThat(derive("ACTIVE", past, LocalDate.of(2026, 11, 20))).isEqualTo(LicenseEffectiveStatus.EXPIRING_SOON);
        assertThat(derive("ACTIVE", past, LocalDate.of(2027, 6, 1))).isEqualTo(LicenseEffectiveStatus.VALID);
    }

    @Test
    void boundaryDays() {
        LocalDate past = TODAY.minusYears(1);
        assertThat(derive("ACTIVE", past, TODAY)).as("expires today is still valid").isEqualTo(LicenseEffectiveStatus.EXPIRING_SOON);
        assertThat(derive("ACTIVE", past, TODAY.minusDays(1))).isEqualTo(LicenseEffectiveStatus.EXPIRED);
        assertThat(derive("ACTIVE", past, TODAY.plusDays(30))).as("day 30 is expiring soon").isEqualTo(LicenseEffectiveStatus.EXPIRING_SOON);
        assertThat(derive("ACTIVE", past, TODAY.plusDays(31))).as("day 31 is valid").isEqualTo(LicenseEffectiveStatus.VALID);
        assertThat(derive("ACTIVE", TODAY, TODAY.plusYears(1))).as("issued today is valid").isEqualTo(LicenseEffectiveStatus.VALID);
        assertThat(derive("ACTIVE", TODAY.plusDays(1), TODAY.plusYears(1))).isEqualTo(LicenseEffectiveStatus.NOT_YET_VALID);
    }

    @Test
    void revokedWinsOverEverything() {
        assertThat(derive("REVOKED", TODAY.minusYears(1), TODAY.plusYears(1))).isEqualTo(LicenseEffectiveStatus.REVOKED);
        assertThat(derive("REVOKED", TODAY.minusYears(2), TODAY.minusYears(1))).isEqualTo(LicenseEffectiveStatus.REVOKED);
        assertThat(derive("REVOKED", TODAY.plusDays(5), TODAY.plusYears(1))).isEqualTo(LicenseEffectiveStatus.REVOKED);
    }

    @Test
    void validAndExpiringSoonCountAsCurrentlyValid() {
        assertThat(LicenseEffectiveStatus.VALID.isCurrentlyValid()).isTrue();
        assertThat(LicenseEffectiveStatus.EXPIRING_SOON.isCurrentlyValid()).isTrue();
        assertThat(LicenseEffectiveStatus.EXPIRED.isCurrentlyValid()).isFalse();
        assertThat(LicenseEffectiveStatus.NOT_YET_VALID.isCurrentlyValid()).isFalse();
        assertThat(LicenseEffectiveStatus.REVOKED.isCurrentlyValid()).isFalse();
    }
}
