package ee.taltech.ite4120.vessels.api;

import ee.taltech.ite4120.animals.ownerregistry.OwnerInfo;
import ee.taltech.ite4120.vessels.dto.VesselQueryParams;
import ee.taltech.ite4120.vessels.model.RetirementReason;
import ee.taltech.ite4120.vessels.model.Vessel;
import ee.taltech.ite4120.vessels.model.VesselType;
import ee.taltech.ite4120.vessels.service.VesselService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.helex.commons.model.QueryResult;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ee.taltech.ite4120.vessels.service.VesselTime;

/** The vessels REST API (VESSELS.01 § API). HTTP only — the rules live in {@link VesselService}. */
@RestController
@RequestMapping("/api/vessels")
@Tag(name = "Vessels", description = "The vessel register: registration, search, update, retirement, owner lookup")
public class VesselController {

    private final VesselService service;

    public VesselController(VesselService service) {
        this.service = service;
    }

    @Operation(summary = "Search vessels — paged; textContains matches code or name; retired=true lists retired vessels")
    @GetMapping
    public QueryResult<Vessel> search(@ParameterObject @ModelAttribute VesselQueryParams params) {
        return service.search(params);
    }

    @Operation(summary = "One vessel by id — active or retired (retired ones carry retiredOn / retirementReason)")
    @ApiResponse(responseCode = "404", description = "No such vessel")
    @GetMapping("/{id}")
    public Vessel byId(@PathVariable Long id) {
        return service.require(id);
    }

    @Operation(summary = "Register a vessel (verifies owner with the population registry and opens the first ownership period when an owner code is given)")
    @ApiResponse(responseCode = "201", description = "Created — Location points at the new vessel")
    @ApiResponse(responseCode = "400", description = "Validation failed (unknown type, future build year, bad or unknown owner code…)")
    @ApiResponse(responseCode = "409", description = "Registry code already in use by an active vessel")
    @ApiResponse(responseCode = "502", description = "Population registry unavailable (when ownerIsikukood is provided)")
    @PostMapping
    public ResponseEntity<Vessel> create(@Valid @RequestBody VesselRequest body) {
        Vessel saved = service.create(body.toModel());
        return ResponseEntity.created(URI.create("/api/vessels/" + saved.getId())).body(saved);
    }

    @Operation(summary = "Update a vessel — registry code is immutable; the owner can only be set when there is none yet (verified live with the registry)")
    @ApiResponse(responseCode = "400", description = "Validation failed, unknown owner, or attempt to change an existing owner")
    @ApiResponse(responseCode = "409", description = "Vessel is retired")
    @ApiResponse(responseCode = "502", description = "Population registry unavailable (when setting initial owner)")
    @PutMapping("/{id}")
    public Vessel update(@PathVariable Long id, @Valid @RequestBody VesselRequest body) {
        return service.update(id, body.toModel());
    }

    @Operation(summary = "Retire a vessel (soft delete, 204 No Content) — records date, reason and note, and revokes every currently valid license")
    @ApiResponse(responseCode = "204", description = "Retired (soft-deleted)")
    @ApiResponse(responseCode = "400", description = "Validation failed (future date, unknown reason, OTHER without note)")
    @ApiResponse(responseCode = "404", description = "No such vessel")
    @ApiResponse(responseCode = "409", description = "Already retired")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retire(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate retiredOn,
            @RequestParam(defaultValue = "SCRAPPED") String reason,
            @RequestParam(required = false) String note) {
        service.retire(id, retiredOn != null ? retiredOn : VesselTime.today(), reason, note);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current owner from the population registry (mock locally, X-Road in production)",
            description = "FAIL OPEN: an unreachable registry answers 502 here only; the vessel stays fully usable.")
    @ApiResponse(responseCode = "502", description = "Population registry unavailable")
    @GetMapping("/{id}/owner")
    public OwnerInfo owner(@PathVariable Long id) {
        return service.owner(id);
    }

    @Operation(summary = "Vessel type reference list")
    @GetMapping("/types")
    public List<VesselType> types() {
        return service.types();
    }

    @Operation(summary = "Retirement reason reference list")
    @GetMapping("/retirement-reasons")
    public List<RetirementReason> retirementReasons() {
        return service.retirementReasons();
    }
}
