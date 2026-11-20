package ee.taltech.ite4120.vessels.dto;

import org.helex.commons.model.QueryParams;

/**
 * Query parameters for the vessel search endpoint (GET /api/vessels).
 * Extends {@link QueryParams} which provides limit, offset, and sort.
 */
public class VesselQueryParams extends QueryParams {

    /** Case-insensitive substring match on name or registry_code. */
    private String textContains;
    /** Exact filter on vessel_type code (e.g. FISHING, CARGO). */
    private String typeCode;
    /** If true, returns retired vessels (sys_status = 'D'); if false (default), returns active vessels (sys_status = 'A'). */
    private Boolean retired = false;

    public String getTextContains() { return textContains; }
    public void setTextContains(String textContains) { this.textContains = textContains; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public Boolean getRetired() { return retired; }
    public void setRetired(Boolean retired) { this.retired = retired; }
}
