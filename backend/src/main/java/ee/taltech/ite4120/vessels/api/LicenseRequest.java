package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.vessels.model.License;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST /api/vessels/{id}/licenses. */
public record LicenseRequest(
        @NotBlank @Size(max = 50) String licenseNumber,
        @NotBlank String licenseType,
        @NotNull LocalDate issuedOn,
        @NotNull LocalDate validUntil) {

    public License toModel() {
        License l = new License();
        l.setLicenseNumber(licenseNumber);
        l.setLicenseType(licenseType);
        l.setIssuedOn(issuedOn);
        l.setValidUntil(validUntil);
        return l;
    }
}
