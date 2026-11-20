package ee.taltech.ite4120.vessels.model;

/**
 * Natural-key reference item for vessel classification.
 * Maps 1:1 onto {@code vessels.vessel_type}.
 */
public class VesselType {

    private String code;
    private String name;

    public VesselType() {}

    public VesselType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
