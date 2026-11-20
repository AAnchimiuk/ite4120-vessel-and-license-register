package ee.taltech.ite4120.vessels.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** Body of POST /api/vessels/{id}/ownership. */
public record TransferRequest(
        @Pattern(regexp = "\\d{11}", message = "newOwnerIsikukood must be exactly 11 digits") String newOwnerIsikukood,
        @NotNull LocalDate transferDate) {}
