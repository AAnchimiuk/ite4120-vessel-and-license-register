package ee.taltech.ite4120.vessels.repository;

import ee.taltech.ite4120.vessels.dto.ExpiringLicenseQueryParams;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseView;
import ee.taltech.ite4120.vessels.model.License;
import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.helex.commons.db.sql.SqlBuilder;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Repository;

/**
 * Data access for operational maritime licenses.
 */
@Repository
public class LicenseRepository extends BaseRepository {

    private static final String SELECT = "select l.* from vessels.license l ";

    private final PgBeanProcessor bp = new PgBeanProcessor(License.class);
    private final PgBeanProcessor expiringBp = new PgBeanProcessor(ExpiringLicenseView.class);

    public License load(Long id) {
        return getBean(SELECT + "where l.id = ? and l.sys_status = 'A'", bp, id);
    }

    public List<License> listByVesselId(Long vesselId) {
        return getBeans(SELECT + "where l.vessel_id = ? and l.sys_status = 'A' order by l.issued_on desc, l.id desc",
                bp, vesselId);
    }

    public Long insert(License l) {
        String sql = """
                insert into vessels.license (vessel_id, license_number, license_type, issued_on, valid_until, status)
                values (?, ?, ?, ?, ?, coalesce(?, 'ACTIVE'))
                returning id""";
        return queryForObject(sql, Long.class,
                l.getVesselId(), l.getLicenseNumber(), l.getLicenseType(),
                l.getIssuedOn(), l.getValidUntil(), l.getStatus());
    }

    public boolean revoke(Long id, LocalDate revokedOn, String reason) {
        String sql = """
                update vessels.license
                   set status = 'REVOKED',
                       revoked_on = ?,
                       revocation_reason = ?
                 where id = ? and status = 'ACTIVE' and sys_status = 'A'""";
        return jdbcTemplate.update(sql, revokedOn, reason, id) == 1;
    }

    /** Soft-deletes a license row (used only for licenses that are NOT_YET_VALID). */
    public boolean delete(Long id) {
        String sql = "update vessels.license set sys_status = 'D' where id = ? and sys_status = 'A'";
        return jdbcTemplate.update(sql, id) == 1;
    }

    /**
     * Atomically revokes all currently valid licenses of a vessel upon retirement.
     */
    public int revokeAllValidForVessel(Long vesselId, LocalDate revokedOn, String reason, LocalDate asOfDate) {
        String sql = """
                update vessels.license
                   set status = 'REVOKED',
                       revoked_on = ?,
                       revocation_reason = ?
                 where vessel_id = ?
                   and status = 'ACTIVE'
                   and valid_until >= ?
                   and issued_on <= ?
                   and sys_status = 'A'""";
        return jdbcTemplate.update(sql, revokedOn, reason, vesselId, asOfDate, revokedOn);
    }

    /**
     * Paged search for licenses expiring within a given window, restricted to active vessels.
     */
    public QueryResult<ExpiringLicenseView> searchExpiring(ExpiringLicenseQueryParams p, LocalDate today) {
        int days = (p.getWithinDays() != null) ? p.getWithinDays() : 30;
        LocalDate cutoff = today.plusDays(days);

        return query(p,
                params -> countExpiring(params, today, cutoff),
                params -> listExpiring(params, today, cutoff));
    }

    private Integer countExpiring(ExpiringLicenseQueryParams p, LocalDate today, LocalDate cutoff) {
        SqlBuilder sb = new SqlBuilder("""
                select count(1)
                  from vessels.license l
                  join vessels.vessel v on v.id = l.vessel_id
                 where v.sys_status = 'A'
                   and l.sys_status = 'A'
                   and l.status = 'ACTIVE'""");
        sb.append("and l.issued_on <= ?", today);
        sb.append("and l.valid_until >= ?", today);
        sb.append("and l.valid_until <= ?", cutoff);
        sb.appendIfNotNull("and l.license_type = ?", p.getLicenseType());
        return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }

    private List<ExpiringLicenseView> listExpiring(ExpiringLicenseQueryParams p, LocalDate today, LocalDate cutoff) {
        SqlBuilder sb = new SqlBuilder("""
                select v.id as vessel_id,
                       v.registry_code as registry_code,
                       v.name as vessel_name,
                       l.id as license_id,
                       l.license_number as license_number,
                       l.license_type as license_type,
                       l.issued_on as issued_on,
                       l.valid_until as valid_until
                  from vessels.license l
                  join vessels.vessel v on v.id = l.vessel_id
                 where v.sys_status = 'A'
                   and l.sys_status = 'A'
                   and l.status = 'ACTIVE'""");
        sb.append("and l.issued_on <= ?", today);
        sb.append("and l.valid_until >= ?", today);
        sb.append("and l.valid_until <= ?", cutoff);
        sb.appendIfNotNull("and l.license_type = ?", p.getLicenseType());
        sb.append("order by l.valid_until asc, l.id asc");
        sb.append(limit(p));
        List<ExpiringLicenseView> items = getBeans(sb.getSql(), expiringBp, sb.getParams());
        for (ExpiringLicenseView v : items) {
            v.setEffectiveStatus(LicenseEffectiveStatus.derive("ACTIVE", v.getIssuedOn(), v.getValidUntil(), today));
        }
        return items;
    }
}
