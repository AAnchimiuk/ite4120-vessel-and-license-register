package ee.taltech.ite4120.vessels.dto;

import ee.taltech.ite4120.vessels.model.LicenseEffectiveStatus;
import java.time.LocalDate;

/**
 * Projection combining license and active vessel information for the expiring licenses list.
 */
public class ExpiringLicenseView {

    private Long vesselId;
    private String registryCode;
    private String vesselName;
    private Long licenseId;
    private String licenseNumber;
    private String licenseType;
    private LocalDate issuedOn;
    private LocalDate validUntil;
    private LicenseEffectiveStatus effectiveStatus;

    public Long getVesselId() { return vesselId; }
    public void setVesselId(Long vesselId) { this.vesselId = vesselId; }
    public String getRegistryCode() { return registryCode; }
    public void setRegistryCode(String registryCode) { this.registryCode = registryCode; }
    public String getVesselName() { return vesselName; }
    public void setVesselName(String vesselName) { this.vesselName = vesselName; }
    public Long getLicenseId() { return licenseId; }
    public void setLicenseId(Long licenseId) { this.licenseId = licenseId; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
    public LocalDate getIssuedOn() { return issuedOn; }
    public void setIssuedOn(LocalDate issuedOn) { this.issuedOn = issuedOn; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public LicenseEffectiveStatus getEffectiveStatus() { return effectiveStatus; }
    public void setEffectiveStatus(LicenseEffectiveStatus effectiveStatus) { this.effectiveStatus = effectiveStatus; }
}
