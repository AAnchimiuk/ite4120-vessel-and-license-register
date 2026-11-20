package ee.taltech.ite4120.vessels.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body of POST /api/vessels/{id}/retirement. The note is required only for reason OTHER (service rule). */
public record RetirementRequest(
        @NotNull LocalDate retiredOn,
        @NotBlank String reason,
        @Size(max = 500) String note) {}
