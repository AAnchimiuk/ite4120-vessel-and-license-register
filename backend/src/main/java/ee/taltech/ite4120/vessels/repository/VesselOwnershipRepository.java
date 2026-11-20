package ee.taltech.ite4120.vessels.repository;

import ee.taltech.ite4120.vessels.model.VesselOwnership;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.springframework.stereotype.Repository;

/**
 * Data access for vessel ownership history periods.
 */
@Repository
public class VesselOwnershipRepository extends BaseRepository {

    private static final String SELECT = "select o.* from vessels.vessel_ownership o ";

    private final PgBeanProcessor bp = new PgBeanProcessor(VesselOwnership.class);

    public VesselOwnership load(Long id) {
        return getBean(SELECT + "where o.id = ? and o.sys_status = 'A'", bp, id);
    }

    public VesselOwnership loadCurrentByVesselId(Long vesselId) {
        return getBean(SELECT + "where o.vessel_id = ? and o.owned_until is null and o.sys_status = 'A'",
                bp, vesselId);
    }

    public List<VesselOwnership> listHistoryByVesselId(Long vesselId) {
        return getBeans(SELECT + "where o.vessel_id = ? and o.sys_status = 'A' order by o.owned_from desc, o.id desc",
                bp, vesselId);
    }

    public Long insert(VesselOwnership o) {
        String sql = """
                insert into vessels.vessel_ownership (vessel_id, owner_isikukood, owned_from, owned_until)
                values (?, ?, ?, ?)
                returning id""";
        return queryForObject(sql, Long.class,
                o.getVesselId(), o.getOwnerIsikukood(), o.getOwnedFrom(), o.getOwnedUntil());
    }

    public boolean closeCurrentPeriod(Long vesselId, LocalDate transferDate) {
        String sql = """
                update vessels.vessel_ownership
                   set owned_until = ?
                 where vessel_id = ?
                   and owned_until is null
                   and sys_status = 'A'""";
        return jdbcTemplate.update(sql, transferDate, vesselId) == 1;
    }
}
