package ee.taltech.ite4120.vessels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ee.taltech.ite4120.vessels.dto.ExpiringLicenseQueryParams;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseView;
import ee.taltech.ite4120.vessels.dto.VesselQueryParams;
import ee.taltech.ite4120.vessels.model.License;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselOwnership;
import ee.taltech.ite4120.vessels.repository.LicenseRepository;
import ee.taltech.ite4120.vessels.repository.VesselOwnershipRepository;
import ee.taltech.ite4120.vessels.repository.VesselRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.exception.ApiClientException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * DATA LAYER TESTS for the vessels component: the Liquibase changelog
 * (vessels schema) and the three repositories, run against a throwaway
 * PostgreSQL via Testcontainers. Derived from docs/src/specifications/VESSELS.01
 * (data model, integrity rules and business tests) — every constraint the
 * specification names is attacked here with a row the database must refuse.
 *
 * <p>The whole changelog (core + animals + vessels, demo context included) is
 * applied from empty, so these tests also prove the vessels migrations run clean.
 * Each test uses its own {@code DL-*} registry codes, so tests are independent of
 * each other and of the demo rows.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class VesselDataLayerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired VesselRepository vessels;
    @Autowired LicenseRepository licenses;
    @Autowired VesselOwnershipRepository ownerships;
    @Autowired JdbcTemplate jdbc;

    // ---------------------------------------------------------------- migrations

    @Test
    void migrationsCreateTheVesselsSchemaAndSeedReferenceLists() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'vessels'", String.class);
        assertThat(tables).contains("vessel_type", "retirement_reason", "vessel", "license", "vessel_ownership");

        assertThat(vessels.listVesselTypes()).extracting("code")
                .containsExactlyInAnyOrder("FISHING", "CARGO", "PASSENGER", "PLEASURE", "SAILING", "TUG");
        assertThat(vessels.listRetirementReasons()).extracting("code")
                .containsExactlyInAnyOrder("SCRAPPED", "LOST", "REFLAGGED", "OTHER");
        assertThat(vessels.vesselTypeExists("CARGO")).isTrue();
        assertThat(vessels.vesselTypeExists("SUBMARINE")).isFalse();
        assertThat(vessels.retirementReasonExists("LOST")).isTrue();
        assertThat(vessels.retirementReasonExists("STOLEN")).isFalse();
    }

    @Test
    void demoDataIsSeededWithActiveAndRetiredVessels() {
        assertThat(vessels.loadActiveByRegistryCode("EST-1001")).isNotNull();
        assertThat(vessels.loadActiveByRegistryCode("EST-9999")).as("retired demo vessel is not active").isNull();
        VesselQueryParams retired = new VesselQueryParams();
        retired.setRetired(true);
        assertThat(vessels.search(retired).getData()).extracting("registryCode").contains("EST-9999");
    }

    // ------------------------------------------------------------- sys_* columns

    @Test
    void platformTriggerFillsSysColumnsAndBumpsTheVersion() {
        Long id = vessels.insert(vessel("DL-SYS-001"));
        Vessel created = vessels.load(id);
        assertThat(created.getSysStatus()).isEqualTo("A");
        assertThat(created.getSysCreatedAt()).isNotNull();
        assertThat(created.getSysCreatedBy()).isNotBlank();
        assertThat(created.getSysVersion()).isEqualTo(1L);

        created.setName("Renamed");
        assertThat(vessels.update(created)).isTrue();
        Vessel updated = vessels.load(id);
        assertThat(updated.getName()).isEqualTo("Renamed");
        assertThat(updated.getSysVersion()).isGreaterThan(created.getSysVersion());
    }

    // ------------------------------------------ registry code uniqueness (rule 2)

    @Test
    void registryCodeIsUniqueAmongActiveVesselsAndReusableAfterRetirement() {
        Long first = vessels.insert(vessel("DL-DUP-001"));

        assertThatThrownBy(() -> vessels.insert(vessel("DL-DUP-001")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(vessels.retire(first, LocalDate.now(), "SCRAPPED", null)).isTrue();
        assertThat(vessels.loadActiveByRegistryCode("DL-DUP-001")).as("retired vessel leaves the active set").isNull();
        assertThat(vessels.load(first).getSysStatus()).isEqualTo("D");

        Long reused = vessels.insert(vessel("DL-DUP-001"));
        assertThat(reused).isNotEqualTo(first);
    }

    @Test
    void retiringTwiceChangesNothing() {
        Long id = vessels.insert(vessel("DL-RET-001"));
        assertThat(vessels.retire(id, LocalDate.now(), "LOST", null)).isTrue();
        assertThat(vessels.retire(id, LocalDate.now(), "LOST", null)).isFalse();
    }

    // ------------------------------------------------------- vessel constraints

    @Test
    void vesselConstraintsRefuseNonsense() {
        Vessel badCode = vessel("DL-CHK-001");
        badCode.setOwnerIsikukood("1234");
        assertRefused(badCode, "owner code must be exactly 11 digits");

        Vessel letters = vessel("DL-CHK-002");
        letters.setOwnerIsikukood("3810213026X");
        assertRefused(letters, "owner code must be digits only");

        Vessel zeroTonnage = vessel("DL-CHK-003");
        zeroTonnage.setGrossTonnage(BigDecimal.ZERO);
        assertRefused(zeroTonnage, "gross tonnage must be positive");

        Vessel ancient = vessel("DL-CHK-004");
        ancient.setBuildYear(1799);
        assertRefused(ancient, "build year below 1800");

        Vessel unknownType = vessel("DL-CHK-005");
        unknownType.setTypeCode("SUBMARINE");
        assertRefused(unknownType, "type must exist in vessel_type (FK)");

        Vessel noName = vessel("DL-CHK-006");
        noName.setName(null);
        assertRefused(noName, "name is required");
    }

    @Test
    void retirementFieldsAreConsistent() {
        Long id = vessels.insert(vessel("DL-RET-002"));
        assertThatThrownBy(() -> jdbc.update(
                "update vessels.vessel set retired_on = ? where id = ?", LocalDate.now(), id))
                .as("retired_on without a reason").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "update vessels.vessel set retired_on = ?, retirement_reason = 'STOLEN' where id = ?",
                LocalDate.now(), id))
                .as("unknown reason (FK)").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> vessels.retire(id, LocalDate.now(), "OTHER", null))
                .as("OTHER needs a note").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> vessels.retire(id, LocalDate.now(), "OTHER", "   "))
                .as("blank note is no note").isInstanceOf(DataIntegrityViolationException.class);
        assertThat(vessels.retire(id, LocalDate.now(), "OTHER", "Sunk during a storm")).isTrue();
    }

    // ----------------------------------------------------------- search / paging

    @Test
    void searchFiltersByTextTypeAndRetiredFlag() {
        Vessel a = vessel("DL-SRCH-A");
        a.setName("Zebra Searchable");
        a.setTypeCode("TUG");
        Vessel b = vessel("DL-SRCH-B");
        b.setName("Yak Searchable");
        b.setTypeCode("CARGO");
        vessels.insert(a);
        Long retiredId = vessels.insert(b);
        vessels.retire(retiredId, LocalDate.now(), "LOST", null);

        VesselQueryParams byName = new VesselQueryParams();
        byName.setTextContains("searchable");
        assertThat(vessels.search(byName).getData()).extracting("registryCode").containsExactly("DL-SRCH-A");

        VesselQueryParams byCode = new VesselQueryParams();
        byCode.setTextContains("dl-srch-a");
        assertThat(vessels.search(byCode).getData()).extracting("registryCode").containsExactly("DL-SRCH-A");

        VesselQueryParams wrongType = new VesselQueryParams();
        wrongType.setTextContains("searchable");
        wrongType.setTypeCode("CARGO");
        assertThat(vessels.search(wrongType).getData()).as("B is CARGO but retired").isEmpty();

        VesselQueryParams retired = new VesselQueryParams();
        retired.setTextContains("searchable");
        retired.setRetired(true);
        assertThat(vessels.search(retired).getData()).extracting("registryCode").containsExactly("DL-SRCH-B");
    }

    @Test
    void searchRejectsAnUnknownSortKey() {
        VesselQueryParams p = new VesselQueryParams();
        p.setSort(List.of("passwordHash"));
        assertThatThrownBy(() -> vessels.search(p))
                .isInstanceOf(ApiClientException.class)
                .hasMessageContaining("sortable");
    }

    // -------------------------------------------------------------- licenses

    @Test
    void licenseConstraintsRefuseNonsense() {
        Long vesselId = vessels.insert(vessel("DL-LIC-001"));
        LocalDate today = LocalDate.now();

        License backwards = license(vesselId, "L-1", "NAVIGATION", today, today.minusDays(1));
        assertThatThrownBy(() -> licenses.insert(backwards))
                .as("validUntil must not precede issuedOn").isInstanceOf(DataIntegrityViolationException.class);

        License sameDay = license(vesselId, "L-2", "NAVIGATION", today, today);
        assertThat(licenses.insert(sameDay)).as("same-day license is allowed").isNotNull();

        License badStatus = license(vesselId, "L-3", "NAVIGATION", today, today.plusDays(10));
        badStatus.setStatus("PENDING");
        assertThatThrownBy(() -> licenses.insert(badStatus))
                .as("status is ACTIVE or REVOKED only").isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into vessels.license (vessel_id, license_number, license_type, issued_on, valid_until, status)"
                        + " values (?, 'L-4', 'NAVIGATION', ?, ?, 'REVOKED')",
                vesselId, today, today.plusDays(10)))
                .as("REVOKED needs revoked_on and a reason").isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into vessels.license (vessel_id, license_number, license_type, issued_on, valid_until, revoked_on)"
                        + " values (?, 'L-5', 'NAVIGATION', ?, ?, ?)",
                vesselId, today, today.plusDays(10), today))
                .as("ACTIVE license must not carry revocation data").isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.update(
                "insert into vessels.license (vessel_id, license_number, license_type, issued_on, valid_until)"
                        + " values (999999999, 'L-6', 'NAVIGATION', ?, ?)", today, today.plusDays(10)))
                .as("vessel must exist (FK)").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void revokingAValidLicenseWorksOnceAndBlankReasonIsRefused() {
        Long vesselId = vessels.insert(vessel("DL-REV-001"));
        LocalDate today = LocalDate.now();
        Long licenseId = licenses.insert(license(vesselId, "R-1", "FISHING", today.minusDays(30), today.plusDays(90)));

        assertThatThrownBy(() -> licenses.revoke(licenseId, today, "  "))
                .as("blank revocation reason").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> licenses.revoke(licenseId, today.minusDays(60), "before issue"))
                .as("revoked_on before issued_on").isInstanceOf(DataIntegrityViolationException.class);

        assertThat(licenses.revoke(licenseId, today, "Failed inspection")).isTrue();
        assertThat(licenses.revoke(licenseId, today, "Again")).as("already revoked").isFalse();

        License revoked = licenses.load(licenseId);
        assertThat(revoked.getStatus()).isEqualTo("REVOKED");
        assertThat(revoked.getRevokedOn()).isEqualTo(today);
        assertThat(revoked.getRevocationReason()).isEqualTo("Failed inspection");
    }

    @Test
    void licenseHistoryKeepsAllLicensesNewestIssuedFirst() {
        Long vesselId = vessels.insert(vessel("DL-HIST-001"));
        LocalDate today = LocalDate.now();
        licenses.insert(license(vesselId, "H-OLD", "NAVIGATION", today.minusYears(3), today.minusYears(2)));
        licenses.insert(license(vesselId, "H-NEW", "NAVIGATION", today.minusDays(5), today.plusYears(1)));

        assertThat(licenses.listByVesselId(vesselId)).extracting("licenseNumber")
                .containsExactly("H-NEW", "H-OLD");
    }

    @Test
    void retirementRevokesEveryCurrentlyValidLicenseAndLeavesExpiredOnesAlone() {
        Long vesselId = vessels.insert(vessel("DL-RETLIC-001"));
        LocalDate today = LocalDate.now();
        Long valid = licenses.insert(license(vesselId, "V-1", "NAVIGATION", today.minusDays(10), today.plusDays(100)));
        Long soon = licenses.insert(license(vesselId, "V-2", "FISHING", today.minusDays(10), today.plusDays(5)));
        Long expired = licenses.insert(license(vesselId, "V-3", "SAFETY", today.minusYears(2), today.minusDays(1)));

        int revoked = licenses.revokeAllValidForVessel(vesselId, today, "Vessel retired", today);

        assertThat(revoked).isEqualTo(2);
        assertThat(licenses.load(valid).getStatus()).isEqualTo("REVOKED");
        assertThat(licenses.load(valid).getRevocationReason()).isEqualTo("Vessel retired");
        assertThat(licenses.load(soon).getStatus()).isEqualTo("REVOKED");
        assertThat(licenses.load(expired).getStatus()).as("expired stays as it was").isEqualTo("ACTIVE");
    }

    @Test
    void expiringListHonoursWindowTypeRevocationAndRetiredVessels() {
        LocalDate today = LocalDate.now();
        Long active = vessels.insert(vessel("DL-EXP-ACTIVE"));
        Long retired = vessels.insert(vessel("DL-EXP-RETIRED"));
        licenses.insert(license(active, "E-SOON", "DLTYPE-A", today.minusDays(100), today.plusDays(10)));
        licenses.insert(license(active, "E-LATER", "DLTYPE-A", today.minusDays(100), today.plusDays(200)));
        licenses.insert(license(active, "E-GONE", "DLTYPE-A", today.minusDays(400), today.minusDays(1)));
        Long revokedId = licenses.insert(license(active, "E-REVOKED", "DLTYPE-A", today.minusDays(100), today.plusDays(10)));
        licenses.revoke(revokedId, today, "withdrawn");
        licenses.insert(license(retired, "E-RETIRED", "DLTYPE-A", today.minusDays(100), today.plusDays(10)));
        vessels.retire(retired, today, "SCRAPPED", null);

        ExpiringLicenseQueryParams p = new ExpiringLicenseQueryParams();
        p.setLicenseType("DLTYPE-A");
        p.setWithinDays(30);
        assertThat(licenses.searchExpiring(p, today).getData()).extracting(ExpiringLicenseView::getLicenseNumber)
                .containsExactly("E-SOON");

        p.setWithinDays(365);
        assertThat(licenses.searchExpiring(p, today).getData()).extracting(ExpiringLicenseView::getLicenseNumber)
                .containsExactly("E-SOON", "E-LATER");

        ExpiringLicenseView row = licenses.searchExpiring(p, today).getData().get(0);
        assertThat(row.getRegistryCode()).isEqualTo("DL-EXP-ACTIVE");
        assertThat(row.getVesselId()).isEqualTo(active);
    }

    // ------------------------------------------------------------- ownership

    @Test
    void ownershipPeriodsEnforceOneOpenPeriodAndSaneDates() {
        Long vesselId = vessels.insert(vessel("DL-OWN-001"));
        LocalDate today = LocalDate.now();
        ownerships.insert(period(vesselId, "38102130265", today.minusYears(2), null));

        assertThatThrownBy(() -> ownerships.insert(period(vesselId, "47503121234", today.minusYears(1), null)))
                .as("only one open period per vessel").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ownerships.insert(period(vesselId, "47503121234", today, today.minusDays(1))))
                .as("owned_until before owned_from").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> ownerships.insert(period(vesselId, "123", today.minusYears(5), today.minusYears(4))))
                .as("owner code must be 11 digits").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transferClosesTheOpenPeriodAndOpensANewOneWithHistoryNewestFirst() {
        Long vesselId = vessels.insert(vessel("DL-OWN-002"));
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate transfer = LocalDate.of(2026, 11, 1);
        ownerships.insert(period(vesselId, "38102130265", start, null));

        assertThat(ownerships.closeCurrentPeriod(vesselId, transfer)).isTrue();
        ownerships.insert(period(vesselId, "47503121234", transfer, null));
        assertThat(vessels.updateOwnerIsikukood(vesselId, "47503121234")).isTrue();

        VesselOwnership current = ownerships.loadCurrentByVesselId(vesselId);
        assertThat(current.getOwnerIsikukood()).isEqualTo("47503121234");
        assertThat(current.getOwnedFrom()).isEqualTo(transfer);
        assertThat(current.getOwnedUntil()).isNull();

        List<VesselOwnership> history = ownerships.listHistoryByVesselId(vesselId);
        assertThat(history).extracting("ownerIsikukood").containsExactly("47503121234", "38102130265");
        assertThat(history.get(1).getOwnedUntil()).isEqualTo(transfer);
        assertThat(vessels.load(vesselId).getOwnerIsikukood()).isEqualTo("47503121234");

        assertThat(ownerships.closeCurrentPeriod(vesselId, transfer.plusDays(1))).isTrue();
        assertThat(ownerships.closeCurrentPeriod(vesselId, transfer.plusDays(2)))
                .as("nothing left open to close").isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private void assertRefused(Vessel v, String why) {
        assertThatThrownBy(() -> vessels.insert(v)).as(why).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Vessel vessel(String registryCode) {
        Vessel v = new Vessel();
        v.setRegistryCode(registryCode);
        v.setName("Test Vessel " + registryCode);
        v.setTypeCode("CARGO");
        v.setBuildYear(2010);
        v.setHomePort("Tallinn");
        v.setGrossTonnage(new BigDecimal("100.5"));
        return v;
    }

    private static License license(Long vesselId, String number, String type, LocalDate issued, LocalDate until) {
        License l = new License();
        l.setVesselId(vesselId);
        l.setLicenseNumber(number);
        l.setLicenseType(type);
        l.setIssuedOn(issued);
        l.setValidUntil(until);
        return l;
    }

    private static VesselOwnership period(Long vesselId, String isikukood, LocalDate from, LocalDate until) {
        VesselOwnership o = new VesselOwnership();
        o.setVesselId(vesselId);
        o.setOwnerIsikukood(isikukood);
        o.setOwnedFrom(from);
        o.setOwnedUntil(until);
        return o;
    }
}
