package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.vessels.dto.ExpiringLicenseQueryParams;
import ee.taltech.ite4120.vessels.dto.ExpiringLicenseView;
import ee.taltech.ite4120.vessels.dto.LicenseType;
import ee.taltech.ite4120.vessels.service.LicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.helex.commons.model.QueryResult;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Register-wide license views (VESSELS.01 § API). */
@RestController
@RequestMapping("/api/licenses")
@Tag(name = "Licenses", description = "Register-wide license monitoring")
public class LicenseController {

    private final LicenseService service;

    public LicenseController(LicenseService service) {
        this.service = service;
    }

    @Operation(summary = "Licenses expiring within N days (default 30, 1–365), soonest first, active vessels only")
    @ApiResponse(responseCode = "400", description = "withinDays outside 1–365")
    @GetMapping("/expiring")
    public QueryResult<ExpiringLicenseView> expiring(@ParameterObject @ModelAttribute ExpiringLicenseQueryParams params) {
        return service.expiring(params);
    }

    @Operation(summary = "License type reference list")
    @GetMapping("/types")
    public List<LicenseType> types() {
        return service.types();
    }
}
