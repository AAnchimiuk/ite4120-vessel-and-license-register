package ee.taltech.ite4120.vessels.model;

/**
 * Natural-key reference item for vessel retirement reason.
 * Maps 1:1 onto {@code vessels.retirement_reason}.
 */
public class RetirementReason {

    private String code;
    private String name;

    public RetirementReason() {}

    public RetirementReason(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
