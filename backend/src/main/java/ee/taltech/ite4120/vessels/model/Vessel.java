package ee.taltech.ite4120.vessels.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One registered vessel. Maps 1:1 onto {@code vessels.vessel}.
 *
 * <p>The {@code sys*} fields are filled by the shared {@code core.sys_columns()}
 * trigger, never by application code.
 */
public class Vessel {

    private Long id;
    /** Registry code, e.g. EST-1001. Unique among active rows (where sys_status = 'A'). */
    private String registryCode;
    private String name;
    /** FK → vessels.vessel_type(code). */
    private String typeCode;
    private Integer buildYear;
    private String homePort;
    private BigDecimal grossTonnage;
    /** Estonian personal code of the owner. A string of 11 digits, never a number. */
    private String ownerIsikukood;

    /** Retirement fields: set when a vessel is decommissioned. */
    private LocalDate retiredOn;
    /** FK → vessels.retirement_reason(code). */
    private String retirementReason;
    private String retirementNote;

    private String sysStatus;
    private Long sysVersion;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;
    private OffsetDateTime sysModifiedAt;
    private String sysModifiedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegistryCode() { return registryCode; }
    public void setRegistryCode(String registryCode) { this.registryCode = registryCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Integer getBuildYear() { return buildYear; }
    public void setBuildYear(Integer buildYear) { this.buildYear = buildYear; }
    public String getHomePort() { return homePort; }
    public void setHomePort(String homePort) { this.homePort = homePort; }
    public BigDecimal getGrossTonnage() { return grossTonnage; }
    public void setGrossTonnage(BigDecimal grossTonnage) { this.grossTonnage = grossTonnage; }
    public String getOwnerIsikukood() { return ownerIsikukood; }
    public void setOwnerIsikukood(String ownerIsikukood) { this.ownerIsikukood = ownerIsikukood; }
    public LocalDate getRetiredOn() { return retiredOn; }
    public void setRetiredOn(LocalDate retiredOn) { this.retiredOn = retiredOn; }
    public String getRetirementReason() { return retirementReason; }
    public void setRetirementReason(String retirementReason) { this.retirementReason = retirementReason; }
    public String getRetirementNote() { return retirementNote; }
    public void setRetirementNote(String retirementNote) { this.retirementNote = retirementNote; }
    public String getSysStatus() { return sysStatus; }
    public void setSysStatus(String sysStatus) { this.sysStatus = sysStatus; }
    public Long getSysVersion() { return sysVersion; }
    public void setSysVersion(Long sysVersion) { this.sysVersion = sysVersion; }
    public OffsetDateTime getSysCreatedAt() { return sysCreatedAt; }
    public void setSysCreatedAt(OffsetDateTime sysCreatedAt) { this.sysCreatedAt = sysCreatedAt; }
    public OffsetDateTime getSysModifiedAt() { return sysModifiedAt; }
    public void setSysModifiedAt(OffsetDateTime sysModifiedAt) { this.sysModifiedAt = sysModifiedAt; }
    public String getSysCreatedBy() { return sysCreatedBy; }
    public void setSysCreatedBy(String sysCreatedBy) { this.sysCreatedBy = sysCreatedBy; }
    public String getSysModifiedBy() { return sysModifiedBy; }
    public void setSysModifiedBy(String sysModifiedBy) { this.sysModifiedBy = sysModifiedBy; }
}
