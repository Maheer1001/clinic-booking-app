package model;

import java.util.List;

/**
 * A past appointment as the patient sees it in Tracking.
 * @param status how it ended; NO_SHOW (set by staff) is listed as "Missed". Null when not known.
 */
public record AppointmentDetails(String date, String startTime, String endTime, String serviceName, String clinicName,
                                 String notes, AppointmentStatus status) implements TrackingData {

    public AppointmentDetails(String date, String startTime, String endTime, String serviceName, String clinicName, String notes) {
        this(date, startTime, endTime, serviceName, clinicName, notes, null);
    }

    /** True when the clinic marked the patient as not attending. */
    public boolean missed() {
        return status == AppointmentStatus.NO_SHOW;
    }

    public List<String> getNotesLines() {
        if (notes == null) { return List.of(); } // Prevents NPE when notes is null.
        return notes.lines().toList();
    }
}
