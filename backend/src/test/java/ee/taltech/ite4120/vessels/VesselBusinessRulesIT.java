package ee.taltech.ite4120.vessels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ee.taltech.ite4120.animals.ownerregistry.OwnerInfo;
import ee.taltech.ite4120.animals.ownerregistry.OwnerRegistryAdapter;
import ee.taltech.ite4120.animals.ownerregistry.OwnerRegistryException;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseQueryParams;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseView;
import ee.taltech.ite4120.vessels.dto.LicenseView;
import ee.taltech.ite4120.vessels.model.License;
import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselOwnership;
import ee.taltech.ite4120.vessels.service.LicenseService;
import ee.taltech.ite4120.vessels.service.OwnershipService;
import ee.taltech.ite4120.vessels.service.VesselService;
import ee.taltech.ite4120.vessels.service.VesselTime;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.exception.ConflictException;
import org.helex.commons.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * THE BUSINESS TESTS for the vessels component — the executable form of
 * docs/src/specifications/VESSELS.01 § Business tests (numbers in the method
 * comments). Written before the services; they drive {@link VesselService},
 * {@link LicenseService} and {@link OwnershipService} against a real PostgreSQL
 * (Testcontainers), with the population registry replaced by a Mockito mock so
 * "unknown person" and "registry down" can be staged on demand.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class VesselBusinessRulesIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    private static final String OWNER_A = "38102130265";
    private static final String OWNER_B = "47503121234";

    @Autowired VesselService vessels;
    @Autowired LicenseService licenses;
    @Autowired OwnershipService ownership;

    @MockitoBean OwnerRegistryAdapter registry;

    private final LocalDate today = VesselTime.today();

    @BeforeEach
    void registryKnowsEveryoneByDefault() {
        reset(registry);
        when(registry.lookup(anyString())).thenAnswer(inv ->
                new OwnerInfo(inv.getArgument(0), "Test", "Person", "Test street 1"));
    }

    // 1 -------------------------------------------------------------------------
    @Test
    void aVesselCannotHaveABuildYearInTheFuture() {
        Vessel v = vessel("BR-FUT-001");
        v.setBuildYear(today.getYear() + 1);
        assertThatThrownBy(() -> vessels.create(v))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("buildYear must not be in the future");
    }

    @Test
    void unknownVesselTypeIsRefused() {
        Vessel v = vessel("BR-TYPE-001");
        v.setTypeCode("SUBMARINE");
        assertThatThrownBy(() -> vessels.create(v))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("unknown typeCode");
    }

    // 2 -------------------------------------------------------------------------
    @Test
    void aRegistryCodeIsUniqueAmongActiveVesselsAndReusableAfterRetirement() {
        Vessel first = vessels.create(vessel("BR-DUP-001"));

        assertThatThrownBy(() -> vessels.create(vessel("BR-DUP-001")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        vessels.retire(first.getId(), today, "SCRAPPED", null);
        Vessel reused = vessels.create(vessel("BR-DUP-001"));
        assertThat(reused.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void registeringWithAnOwnerOpensTheFirstOwnershipPeriod() {
        Vessel v = vessel("BR-OWN-000");
        v.setOwnerIsikukood(OWNER_A);
        Vessel saved = vessels.create(v);

        List<VesselOwnership> history = ownership.history(saved.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getOwnerIsikukood()).isEqualTo(OWNER_A);
        assertThat(history.get(0).getOwnedFrom()).isEqualTo(today);
        assertThat(history.get(0).getOwnedUntil()).isNull();
    }

    // 3 -------------------------------------------------------------------------
    @Test
    void aLicenseExpirationCannotPrecedeItsIssueDate() {
        Vessel v = vessels.create(vessel("BR-LIC-001"));
        License l = license("L-1", "NAVIGATION", today, today.minusDays(1));
        assertThatThrownBy(() -> licenses.add(v.getId(), l))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("validUntil must not precede issuedOn");
    }

    // 4 -------------------------------------------------------------------------
    @Test
    void licenseEffectiveStatusFollowsTheDates() {
        Vessel v = vessels.create(vessel("BR-STAT-001"));
        LocalDate past = today.minusYears(1);
        licenses.add(v.getId(), license("S-EXPIRED", "NAVIGATION", past, today.minusDays(1)));
        licenses.add(v.getId(), license("S-SOON", "NAVIGATION", past, today.plusDays(14)));
        licenses.add(v.getId(), license("S-VALID", "NAVIGATION", past, today.plusMonths(8)));
        licenses.add(v.getId(), license("S-FUTURE", "NAVIGATION", today.plusDays(3), today.plusYears(1)));

        assertThat(licenses.list(v.getId()))
                .extracting(LicenseView::licenseNumber, LicenseView::effectiveStatus)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("S-EXPIRED", LicenseEffectiveStatus.EXPIRED),
                        org.assertj.core.groups.Tuple.tuple("S-SOON", LicenseEffectiveStatus.EXPIRING_SOON),
                        org.assertj.core.groups.Tuple.tuple("S-VALID", LicenseEffectiveStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("S-FUTURE", LicenseEffectiveStatus.NOT_YET_VALID));
    }

    // 5 -------------------------------------------------------------------------
    @Test
    void aValidLicenseCanBeRevokedOnce() {
        Vessel v = vessels.create(vessel("BR-REV-001"));
        LicenseView l = licenses.add(v.getId(), license("R-1", "FISHING", today.minusDays(30), today.plusDays(200)));

        LicenseView revoked = licenses.revoke(v.getId(), l.id(), today, "Failed inspection");
        assertThat(revoked.effectiveStatus()).isEqualTo(LicenseEffectiveStatus.REVOKED);
        assertThat(revoked.revocationReason()).isEqualTo("Failed inspection");

        assertThatThrownBy(() -> licenses.revoke(v.getId(), l.id(), today, "Again"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already revoked");
    }

    // 6 -------------------------------------------------------------------------
    @Test
    void anExpiredLicenseCannotBeRevoked() {
        Vessel v = vessels.create(vessel("BR-REV-002"));
        LicenseView expired = licenses.add(v.getId(),
                license("R-EXP", "FISHING", today.minusYears(2), today.minusDays(1)));

        assertThatThrownBy(() -> licenses.revoke(v.getId(), expired.id(), today, "Too late"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not currently valid");
    }

    // 7 -------------------------------------------------------------------------
    @Test
    void revocationNeedsAReason() {
        Vessel v = vessels.create(vessel("BR-REV-003"));
        LicenseView l = licenses.add(v.getId(), license("R-2", "FISHING", today.minusDays(5), today.plusDays(200)));

        assertThatThrownBy(() -> licenses.revoke(v.getId(), l.id(), today, "   "))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("reason must not be blank");
    }

    @Test
    void aLicenseOfAnotherVesselIsNotFound() {
        Vessel a = vessels.create(vessel("BR-REV-004"));
        Vessel b = vessels.create(vessel("BR-REV-005"));
        LicenseView l = licenses.add(a.getId(), license("R-3", "FISHING", today.minusDays(5), today.plusDays(200)));

        assertThatThrownBy(() -> licenses.revoke(b.getId(), l.id(), today, "wrong vessel"))
                .isInstanceOf(NotFoundException.class);
    }

    // 8 -------------------------------------------------------------------------
    @Test
    void theExpiringListExcludesRetiredVessels() {
        Vessel active = vessels.create(vessel("BR-EXP-ACTIVE"));
        Vessel retired = vessels.create(vessel("BR-EXP-RETIRED"));
        licenses.add(active.getId(), license("X-ACTIVE", "BR-EXP-TYPE", today.minusDays(60), today.plusDays(10)));
        licenses.add(retired.getId(), license("X-RETIRED", "BR-EXP-TYPE", today.minusDays(60), today.plusDays(10)));
        // retiring revokes the license too, so also stage a license the SQL alone must exclude
        vessels.retire(retired.getId(), today, "LOST", null);

        ExpiringLicenseQueryParams p = new ExpiringLicenseQueryParams();
        p.setLicenseType("BR-EXP-TYPE");
        p.setWithinDays(30);
        assertThat(licenses.expiring(p).getData()).extracting(ExpiringLicenseView::getLicenseNumber)
                .containsExactly("X-ACTIVE");
    }

    @Test
    void theExpiringWindowMustBeBetween1And365Days() {
        ExpiringLicenseQueryParams p = new ExpiringLicenseQueryParams();
        p.setWithinDays(0);
        assertThatThrownBy(() -> licenses.expiring(p)).isInstanceOf(ApiClientException.class);
        p.setWithinDays(366);
        assertThatThrownBy(() -> licenses.expiring(p)).isInstanceOf(ApiClientException.class);
    }

    // 9 -------------------------------------------------------------------------
    @Test
    void aTransferClosesTheOldPeriodAndOpensANewOne() {
        Vessel v = vessels.create(vessel("BR-TRF-001"));
        LocalDate since = LocalDate.of(2024, 1, 1);
        ownership.transfer(v.getId(), OWNER_A, since);
        LocalDate transferDate = today.minusDays(1);

        VesselOwnership created = ownership.transfer(v.getId(), OWNER_B, transferDate);

        assertThat(created.getOwnedFrom()).isEqualTo(transferDate);
        assertThat(created.getOwnedUntil()).isNull();
        List<VesselOwnership> history = ownership.history(v.getId());
        assertThat(history).extracting(VesselOwnership::getOwnerIsikukood).containsExactly(OWNER_B, OWNER_A);
        assertThat(history.get(1).getOwnedUntil()).isEqualTo(transferDate);
        assertThat(vessels.require(v.getId()).getOwnerIsikukood()).isEqualTo(OWNER_B);
    }

    // 10 ------------------------------------------------------------------------
    @Test
    void theNewOwnerMustExistInThePopulationRegistry() {
        Vessel v = vessels.create(vessel("BR-TRF-002"));
        when(registry.lookup("11111111111")).thenReturn(null);

        assertThatThrownBy(() -> ownership.transfer(v.getId(), "11111111111", today))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("not found in the population registry");
        assertThat(ownership.history(v.getId())).isEmpty();
        assertThat(vessels.require(v.getId()).getOwnerIsikukood()).isNull();
    }

    @Test
    void aMalformedPersonalCodeIsRefusedWithoutAskingTheRegistry() {
        Vessel v = vessels.create(vessel("BR-TRF-003"));
        assertThatThrownBy(() -> ownership.transfer(v.getId(), "1234", today))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("11 digits");
        org.mockito.Mockito.verifyNoInteractions(registry);
    }

    // 11 ------------------------------------------------------------------------
    @Test
    void noTransferWhileThePopulationRegistryIsDown() {
        Vessel v = vessels.create(vessel("BR-TRF-004"));
        ownership.transfer(v.getId(), OWNER_A, today.minusDays(10));
        when(registry.lookup(OWNER_B)).thenThrow(new OwnerRegistryException("registry unreachable"));

        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_B, today))
                .isInstanceOf(OwnerRegistryException.class);
        assertThat(ownership.history(v.getId())).hasSize(1);
        assertThat(ownership.history(v.getId()).get(0).getOwnedUntil()).as("old period untouched").isNull();
        assertThat(vessels.require(v.getId()).getOwnerIsikukood()).isEqualTo(OWNER_A);
    }

    // 12 ------------------------------------------------------------------------
    @Test
    void cannotTransferToTheCurrentOwner() {
        Vessel v = vessels.create(vessel("BR-TRF-005"));
        ownership.transfer(v.getId(), OWNER_A, today.minusDays(10));

        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_A, today))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("different from current owner");
    }

    @Test
    void transferDatesMustBeSane() {
        Vessel v = vessels.create(vessel("BR-TRF-006"));
        ownership.transfer(v.getId(), OWNER_A, today.minusDays(10));

        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_B, today.plusDays(1)))
                .as("future date").isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_B, today.minusDays(10)))
                .as("not after the open period started").isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_B, today.minusDays(20)))
                .as("before the open period started").isInstanceOf(ApiClientException.class);
    }

    @Test
    void theOwnerCodeOfAVesselCannotBeEditedOnceSet() {
        Vessel v = vessel("BR-OWN-001");
        v.setOwnerIsikukood(OWNER_A);
        Vessel saved = vessels.create(v);

        Vessel edit = vessel("BR-OWN-001");
        edit.setOwnerIsikukood(OWNER_B);
        assertThatThrownBy(() -> vessels.update(saved.getId(), edit))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("ownership transfer");

        Vessel same = vessel("BR-OWN-001");
        same.setName("Renamed");
        same.setOwnerIsikukood(OWNER_A);
        assertThat(vessels.update(saved.getId(), same).getName()).isEqualTo("Renamed");
    }

    @Test
    void anOwnerCanBeSetOnceOnAVesselThatHadNone() {
        Vessel saved = vessels.create(vessel("BR-OWN-002"));
        Vessel edit = vessel("BR-OWN-002");
        edit.setOwnerIsikukood(OWNER_A);

        Vessel updated = vessels.update(saved.getId(), edit);

        assertThat(updated.getOwnerIsikukood()).isEqualTo(OWNER_A);
        assertThat(ownership.history(saved.getId())).hasSize(1);
    }

    @Test
    void theRegistryCodeIsImmutable() {
        Vessel saved = vessels.create(vessel("BR-IMM-001"));
        Vessel edit = vessel("BR-IMM-CHANGED");
        edit.setName("Other name");
        Vessel updated = vessels.update(saved.getId(), edit);
        assertThat(updated.getRegistryCode()).isEqualTo("BR-IMM-001");
    }

    @Test
    void ownerLookupGoesThroughTheRegistryAdapter() {
        Vessel v = vessel("BR-LOOK-001");
        v.setOwnerIsikukood(OWNER_A);
        Vessel saved = vessels.create(v);
        when(registry.lookup(OWNER_A)).thenReturn(new OwnerInfo(OWNER_A, "Jaan", "Tamm", "Tallinn"));

        OwnerInfo info = vessels.owner(saved.getId());

        assertThat(info.lastName()).isEqualTo("Tamm");
        verify(registry).lookup(OWNER_A);
    }

    @Test
    void ownerLookupFailsOpenOnlyOnTheOwnerEndpoint() {
        Vessel v = vessel("BR-LOOK-002");
        v.setOwnerIsikukood(OWNER_A);
        Vessel saved = vessels.create(v);
        when(registry.lookup(OWNER_A)).thenThrow(new OwnerRegistryException("down"));

        assertThatThrownBy(() -> vessels.owner(saved.getId())).isInstanceOf(OwnerRegistryException.class);
        assertThat(vessels.require(saved.getId()).getName()).as("the vessel itself stays readable").isNotNull();
        assertThat(licenses.list(saved.getId())).isEmpty();
    }

    @Test
    void aVesselWithoutAnOwnerHasNoOwnerToLookUp() {
        Vessel saved = vessels.create(vessel("BR-LOOK-003"));
        assertThatThrownBy(() -> vessels.owner(saved.getId())).isInstanceOf(NotFoundException.class);
    }

    // 13 ------------------------------------------------------------------------
    @Test
    void retirementRevokesValidLicensesAndLeavesExpiredOnesAlone() {
        Vessel v = vessels.create(vessel("BR-RET-001"));
        LicenseView valid = licenses.add(v.getId(), license("T-VALID", "NAVIGATION", today.minusDays(10), today.plusDays(300)));
        LicenseView soon = licenses.add(v.getId(), license("T-SOON", "FISHING", today.minusDays(10), today.plusDays(5)));
        LicenseView expired = licenses.add(v.getId(), license("T-EXP", "SAFETY", today.minusYears(2), today.minusDays(1)));

        Vessel retired = vessels.retire(v.getId(), today, "SCRAPPED", null);

        assertThat(retired.getRetiredOn()).isEqualTo(today);
        assertThat(retired.getRetirementReason()).isEqualTo("SCRAPPED");
        assertThat(retired.getSysStatus()).isEqualTo("D");
        List<LicenseView> after = licenses.list(v.getId());
        assertThat(byNumber(after, "T-VALID").effectiveStatus()).isEqualTo(LicenseEffectiveStatus.REVOKED);
        assertThat(byNumber(after, "T-VALID").revocationReason()).isEqualTo("Vessel retired");
        assertThat(byNumber(after, "T-VALID").revokedOn()).isEqualTo(today);
        assertThat(byNumber(after, "T-SOON").effectiveStatus()).isEqualTo(LicenseEffectiveStatus.REVOKED);
        assertThat(byNumber(after, "T-EXP").effectiveStatus()).isEqualTo(LicenseEffectiveStatus.EXPIRED);
        assertThat(valid.id()).isNotNull();
        assertThat(soon.id()).isNotNull();
        assertThat(expired.id()).isNotNull();
    }

    // 14 ------------------------------------------------------------------------
    @Test
    void aVesselCannotBeRetiredTwice() {
        Vessel v = vessels.create(vessel("BR-RET-002"));
        vessels.retire(v.getId(), today, "LOST", null);

        assertThatThrownBy(() -> vessels.retire(v.getId(), today, "LOST", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already retired");
    }

    // 15 ------------------------------------------------------------------------
    @Test
    void retirementWithReasonOtherNeedsANote() {
        Vessel v = vessels.create(vessel("BR-RET-003"));
        assertThatThrownBy(() -> vessels.retire(v.getId(), today, "OTHER", null))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("retirementNote is required");
        assertThatThrownBy(() -> vessels.retire(v.getId(), today, "OTHER", "  "))
                .isInstanceOf(ApiClientException.class);
        assertThat(vessels.retire(v.getId(), today, "OTHER", "Sunk in a storm").getRetirementNote())
                .isEqualTo("Sunk in a storm");
    }

    @Test
    void retirementDateAndReasonAreValidated() {
        Vessel v = vessels.create(vessel("BR-RET-004"));
        assertThatThrownBy(() -> vessels.retire(v.getId(), today.plusDays(1), "LOST", null))
                .as("future date").isInstanceOf(ApiClientException.class);
        assertThatThrownBy(() -> vessels.retire(v.getId(), today, "STOLEN", null))
                .as("unknown reason").isInstanceOf(ApiClientException.class);
        assertThat(vessels.require(v.getId()).getSysStatus()).as("nothing changed").isEqualTo("A");
    }

    // 16 ------------------------------------------------------------------------
    @Test
    void aRetiredVesselStaysReadableButFrozen() {
        Vessel v = vessels.create(vessel("BR-FRZ-001"));
        vessels.retire(v.getId(), today, "REFLAGGED", null);

        Vessel read = vessels.require(v.getId());
        assertThat(read.getRetiredOn()).isEqualTo(today);
        assertThat(licenses.list(v.getId())).isEmpty();
        assertThat(ownership.history(v.getId())).isEmpty();

        assertThatThrownBy(() -> licenses.add(v.getId(), license("F-1", "NAVIGATION", today, today.plusDays(10))))
                .isInstanceOf(ConflictException.class).hasMessageContaining("retired");
        assertThatThrownBy(() -> ownership.transfer(v.getId(), OWNER_A, today))
                .isInstanceOf(ConflictException.class).hasMessageContaining("retired");
        assertThatThrownBy(() -> vessels.update(v.getId(), vessel("BR-FRZ-001")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("retired");
    }

    @Test
    void retiredVesselsAreListedOnlyWhenAskedFor() {
        Vessel v = vessels.create(vessel("BR-LST-001"));
        vessels.retire(v.getId(), today, "SCRAPPED", null);

        ee.taltech.ite4120.vessels.dto.VesselQueryParams active = new ee.taltech.ite4120.vessels.dto.VesselQueryParams();
        active.setTextContains("BR-LST-001");
        assertThat(vessels.search(active).getData()).isEmpty();

        ee.taltech.ite4120.vessels.dto.VesselQueryParams retired = new ee.taltech.ite4120.vessels.dto.VesselQueryParams();
        retired.setTextContains("BR-LST-001");
        retired.setRetired(true);
        assertThat(vessels.search(retired).getData()).hasSize(1);
    }

    @Test
    void anUnknownVesselIsNotFound() {
        assertThatThrownBy(() -> vessels.require(-1L)).isInstanceOf(NotFoundException.class);
    }

    // helpers -------------------------------------------------------------------

    private static LicenseView byNumber(List<LicenseView> list, String number) {
        return list.stream().filter(l -> l.licenseNumber().equals(number)).findFirst().orElseThrow();
    }

    private static Vessel vessel(String registryCode) {
        Vessel v = new Vessel();
        v.setRegistryCode(registryCode);
        v.setName("Test vessel " + registryCode);
        v.setTypeCode("CARGO");
        v.setBuildYear(2010);
        v.setHomePort("Tallinn");
        v.setGrossTonnage(new BigDecimal("120"));
        return v;
    }

    private static License license(String number, String type, LocalDate issued, LocalDate until) {
        License l = new License();
        l.setLicenseNumber(number);
        l.setLicenseType(type);
        l.setIssuedOn(issued);
        l.setValidUntil(until);
        return l;
    }
}
