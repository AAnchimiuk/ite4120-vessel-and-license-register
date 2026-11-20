package ee.taltech.ite4120.vessels.dto;

import org.helex.commons.model.QueryParams;

/**
 * Query parameters for the expiring licenses endpoint (GET /api/licenses/expiring).
 */
public class ExpiringLicenseQueryParams extends QueryParams {

    /** Number of days within which the license expires (default 30, range 1-365). */
    private Integer withinDays = 30;
    /** Optional filter by license type (e.g. NAVIGATION, FISHING). */
    private String licenseType;

    public Integer getWithinDays() { return withinDays; }
    public void setWithinDays(Integer withinDays) { this.withinDays = withinDays; }
    public String getLicenseType() { return licenseType; }
    public void setLicenseType(String licenseType) { this.licenseType = licenseType; }
}
