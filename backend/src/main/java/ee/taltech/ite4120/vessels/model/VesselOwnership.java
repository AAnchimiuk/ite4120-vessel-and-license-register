package ee.taltech.ite4120.vessels.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * An ownership period for a vessel. Maps 1:1 onto {@code vessels.vessel_ownership}.
 *
 * <p>The {@code sys*} fields are filled by the shared {@code core.sys_columns()}
 * trigger, never by application code.
 */
public class VesselOwnership {

    private Long id;
    /** FK → vessels.vessel(id). */
    private Long vesselId;
    /** Estonian personal code of the owner (isikukood). Exactly 11 digits string. */
    private String ownerIsikukood;
    private LocalDate ownedFrom;
    /** Null represents the currently active owner. */
    private LocalDate ownedUntil;

    private String sysStatus;
    private Long sysVersion;
    private OffsetDateTime sysCreatedAt;
    private String sysCreatedBy;
    private OffsetDateTime sysModifiedAt;
    private String sysModifiedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVesselId() { return vesselId; }
    public void setVesselId(Long vesselId) { this.vesselId = vesselId; }
    public String getOwnerIsikukood() { return ownerIsikukood; }
    public void setOwnerIsikukood(String ownerIsikukood) { this.ownerIsikukood = ownerIsikukood; }
    public LocalDate getOwnedFrom() { return ownedFrom; }
    public void setOwnedFrom(LocalDate ownedFrom) { this.ownedFrom = ownedFrom; }
    public LocalDate getOwnedUntil() { return ownedUntil; }
    public void setOwnedUntil(LocalDate ownedUntil) { this.ownedUntil = ownedUntil; }
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
