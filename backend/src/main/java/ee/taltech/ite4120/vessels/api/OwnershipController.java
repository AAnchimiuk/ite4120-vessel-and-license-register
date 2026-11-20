package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.animals.ownerregistry.OwnerInfo;
import ee.taltech.ite4120.vessels.model.VesselOwnership;
import ee.taltech.ite4120.vessels.service.OwnershipService;
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

/** Ownership history and transfer (VESSELS.01 § API). */
@RestController
@RequestMapping("/api/vessels/{vesselId}/ownership")
@Tag(name = "Vessel ownership", description = "Dated ownership periods and live-verified transfers")
public class OwnershipController {

    private final OwnershipService service;

    public OwnershipController(OwnershipService service) {
        this.service = service;
    }

    @Operation(summary = "Transfer the vessel to a new owner",
            description = "FAIL CLOSED: the new code is verified with the population registry first; "
                    + "if the registry is down nothing is saved and the answer is 502.")
    @ApiResponse(responseCode = "201", description = "Created — the new open ownership period")
    @ApiResponse(responseCode = "400", description = "Bad code, unknown person, same owner, bad date")
    @ApiResponse(responseCode = "409", description = "Vessel is retired")
    @ApiResponse(responseCode = "502", description = "Population registry unavailable")
    @PostMapping
    public ResponseEntity<VesselOwnership> transfer(@PathVariable Long vesselId,
            @Valid @RequestBody TransferRequest body) {
        VesselOwnership created = service.transfer(vesselId, body.newOwnerIsikukood(), body.transferDate());
        return ResponseEntity.created(URI.create("/api/vessels/" + vesselId + "/ownership")).body(created);
    }

    @Operation(summary = "Ownership history, newest first")
    @GetMapping
    public List<VesselOwnership> history(@PathVariable Long vesselId) {
        return service.history(vesselId);
    }

    @Operation(summary = "Name and address of the owner of one historical period (live from the registry)")
    @ApiResponse(responseCode = "502", description = "Population registry unavailable")
    @GetMapping("/{ownershipId}/owner")
    public OwnerInfo owner(@PathVariable Long vesselId, @PathVariable Long ownershipId) {
        return service.ownerOfPeriod(vesselId, ownershipId);
    }
}
