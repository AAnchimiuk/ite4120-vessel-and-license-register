package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.vessels.dto.LicenseView;
import ee.taltech.ite4120.vessels.service.LicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Licenses of one vessel (VESSELS.01 § API). */
@RestController
@RequestMapping("/api/vessels/{vesselId}/licenses")
@Tag(name = "Vessel licenses", description = "License history, issuing and revocation per vessel")
public class VesselLicenseController {

    private final LicenseService service;

    public VesselLicenseController(LicenseService service) {
        this.service = service;
    }

    @Operation(summary = "All licenses of the vessel (history), each with its derived effectiveStatus, newest issued first")
    @GetMapping
    public List<LicenseView> list(@PathVariable Long vesselId) {
        return service.list(vesselId);
    }

    @Operation(summary = "One license of the vessel")
    @GetMapping("/{licenseId}")
    public LicenseView one(@PathVariable Long vesselId, @PathVariable Long licenseId) {
        return service.get(vesselId, licenseId);
    }

    @Operation(summary = "Record a new license")
    @ApiResponse(responseCode = "201", description = "Created — Location points at the license")
    @ApiResponse(responseCode = "400", description = "validUntil precedes issuedOn, missing fields")
    @ApiResponse(responseCode = "409", description = "Vessel is retired")
    @PostMapping
    public ResponseEntity<LicenseView> add(@PathVariable Long vesselId, @Valid @RequestBody LicenseRequest body) {
        LicenseView created = service.add(vesselId, body.toModel());
        return ResponseEntity.created(URI.create("/api/vessels/" + vesselId + "/licenses/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Revoke a currently valid license")
    @ApiResponse(responseCode = "200", description = "The revoked license")
    @ApiResponse(responseCode = "400", description = "Blank reason or bad date")
    @ApiResponse(responseCode = "409", description = "License not currently valid / already revoked, or vessel retired")
    @PostMapping("/{licenseId}/revocation")
    public LicenseView revoke(@PathVariable Long vesselId, @PathVariable Long licenseId,
            @Valid @RequestBody RevocationRequest body) {
        return service.revoke(vesselId, licenseId, body.revokedOn(), body.reason());
    }
}
