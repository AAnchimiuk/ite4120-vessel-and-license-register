package ee.taltech.ite4120.vessels.service;

import java.time.LocalDate;
import java.time.ZoneId;

/** "Today" for every date rule in the register: the Estonian calendar day (VESSELS.01). */
public final class VesselTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Tallinn");

    private VesselTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
