package ee.taltech.ite4120.vessels.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST /api/vessels/{id}/licenses/{licenseId}/revocation. */
public record RevocationRequest(
        @NotNull LocalDate revokedOn,
        @NotBlank(message = "reason must not be blank") @Size(max = 500) String reason) {}
