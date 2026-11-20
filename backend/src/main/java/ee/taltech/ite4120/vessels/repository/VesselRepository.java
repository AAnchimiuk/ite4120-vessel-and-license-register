package ee.taltech.ite4120.vessels.repository;

import ee.taltech.ite4120.vessels.dto.VesselQueryParams;
import ee.taltech.ite4120.vessels.model.RetirementReason;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.helex.commons.db.bean.PgBeanProcessor;
import org.helex.commons.db.repo.BaseRepository;
import org.helex.commons.db.sql.SqlBuilder;
import org.helex.commons.exception.ApiClientException;
import org.helex.commons.model.QueryResult;
import org.springframework.stereotype.Repository;

/**
 * Data access for the vessels register.
 */
@Repository
public class VesselRepository extends BaseRepository {

    private static final String SELECT = "select v.* from vessels.vessel v ";

    private static final Map<String, String> SORT = Map.of(
            "name", "v.name",
            "registryCode", "v.registry_code",
            "buildYear", "v.build_year",
            "grossTonnage", "v.gross_tonnage",
            "retiredOn", "v.retired_on");

    private final PgBeanProcessor bp = new PgBeanProcessor(Vessel.class);
    private final PgBeanProcessor typeBp = new PgBeanProcessor(VesselType.class);
    private final PgBeanProcessor reasonBp = new PgBeanProcessor(RetirementReason.class);

    /** Loads vessel by id, active or retired. */
    public Vessel load(Long id) {
        return getBean(SELECT + "where v.id = ? and v.sys_status in ('A', 'D')", bp, id);
    }

    /** Loads active vessel only. */
    public Vessel loadActive(Long id) {
        return getBean(SELECT + "where v.id = ? and v.sys_status = 'A'", bp, id);
    }

    /** Loads active vessel by registry code. */
    public Vessel loadActiveByRegistryCode(String registryCode) {
        return getBean(SELECT + "where v.registry_code = ? and v.sys_status = 'A'", bp, registryCode);
    }

    /** Paged search for vessels with filtering and sorting. */
    public QueryResult<Vessel> search(VesselQueryParams p) {
        return query(p, this::count, this::list);
    }

    private Integer count(VesselQueryParams p) {
        String status = Boolean.TRUE.equals(p.getRetired()) ? "D" : "A";
        SqlBuilder sb = new SqlBuilder("select count(1) from vessels.vessel v where v.sys_status = '" + status + "'");
        sb.append(filter(p));
        return queryForObject(sb.getSql(), Integer.class, sb.getParams());
    }

    private List<Vessel> list(VesselQueryParams p) {
        String status = Boolean.TRUE.equals(p.getRetired()) ? "D" : "A";
        SqlBuilder sb = new SqlBuilder(SELECT + "where v.sys_status = '" + status + "'");
        sb.append(filter(p));
        sb.append(order(p));
        sb.append(limit(p));
        return getBeans(sb.getSql(), bp, sb.getParams());
    }

    private SqlBuilder order(VesselQueryParams p) {
        try {
            return order(p, SORT);
        } catch (IllegalArgumentException e) {
            throw new ApiClientException("unknown sort key; sortable: "
                    + String.join(", ", SORT.keySet().stream().sorted().toList()));
        }
    }

    private SqlBuilder filter(VesselQueryParams p) {
        SqlBuilder sb = new SqlBuilder();
        if (p.getTextContains() != null && !p.getTextContains().isBlank()) {
            String like = "%" + p.getTextContains().toLowerCase() + "%";
            sb.and("(lower(v.name) like ? or lower(v.registry_code) like ?)", like, like);
        }
        sb.appendIfNotNull("and v.type_code = ?", p.getTypeCode());
        return sb;
    }

    public Long insert(Vessel v) {
        String sql = """
                insert into vessels.vessel (registry_code, name, type_code, build_year,
                                            home_port, gross_tonnage, owner_isikukood)
                values (?, ?, ?, ?, ?, ?, ?)
                returning id""";
        return queryForObject(sql, Long.class,
                v.getRegistryCode(), v.getName(), v.getTypeCode(), v.getBuildYear(),
                v.getHomePort(), v.getGrossTonnage(), v.getOwnerIsikukood());
    }

    public boolean update(Vessel v) {
        String sql = """
                update vessels.vessel
                   set name = ?, type_code = ?, build_year = ?, home_port = ?,
                       gross_tonnage = ?, owner_isikukood = ?
                 where id = ? and sys_status = 'A'""";
        return jdbcTemplate.update(sql,
                v.getName(), v.getTypeCode(), v.getBuildYear(), v.getHomePort(),
                v.getGrossTonnage(), v.getOwnerIsikukood(), v.getId()) == 1;
    }

    public boolean updateOwnerIsikukood(Long id, String newOwnerIsikukood) {
        String sql = "update vessels.vessel set owner_isikukood = ? where id = ? and sys_status = 'A'";
        return jdbcTemplate.update(sql, newOwnerIsikukood, id) == 1;
    }

    public boolean retire(Long id, LocalDate retiredOn, String reason, String note) {
        String sql = """
                update vessels.vessel
                   set sys_status = 'D',
                       retired_on = ?,
                       retirement_reason = ?,
                       retirement_note = ?
                 where id = ? and sys_status = 'A'""";
        return jdbcTemplate.update(sql, retiredOn, reason, note, id) == 1;
    }

    public List<VesselType> listVesselTypes() {
        return getBeans("select t.* from vessels.vessel_type t order by t.name", typeBp);
    }

    public boolean vesselTypeExists(String code) {
        Integer n = queryForObject("select count(1) from vessels.vessel_type where code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    public List<RetirementReason> listRetirementReasons() {
        return getBeans("select r.* from vessels.retirement_reason r order by r.name", reasonBp);
    }

    public boolean retirementReasonExists(String code) {
        Integer n = queryForObject("select count(1) from vessels.retirement_reason where code = ?", Integer.class, code);
        return n != null && n > 0;
    }
}
