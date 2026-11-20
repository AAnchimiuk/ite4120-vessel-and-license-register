package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.vessels.model.Vessel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create/update body. Bean Validation checks the SHAPE here at the edge; the
 * state-dependent rules (unique code, build year vs today, owner immutability)
 * live in VesselService.
 */
public record VesselRequest(
        @NotBlank @Size(max = 50) String registryCode,
        @NotBlank @Size(max = 255) String name,
        @NotBlank String typeCode,
        @Min(1800) Integer buildYear,
        @Size(max = 100) String homePort,
        @Positive BigDecimal grossTonnage,
        /** 11 digits — and a string, never a number. */
        @Pattern(regexp = "\\d{11}", message = "ownerIsikukood must be exactly 11 digits") String ownerIsikukood) {

    public Vessel toModel() {
        Vessel v = new Vessel();
        v.setRegistryCode(registryCode);
        v.setName(name);
        v.setTypeCode(typeCode);
        v.setBuildYear(buildYear);
        v.setHomePort(homePort);
        v.setGrossTonnage(grossTonnage);
        v.setOwnerIsikukood(ownerIsikukood);
        return v;
    }
}
