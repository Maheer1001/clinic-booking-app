package model;

import java.util.List;

/**
 * Upcoming appointment with app_id for reschedule/cancel actions.
 */
public record UpcomingAppointmentDetails(
    int appId,
    String date,
    String startTime,
    String endTime,
    String serviceName,
    String clinicName,
    String notes
) {
    public List<String> getNotesLines() {
        if (notes == null) return List.of();
        return notes.lines().toList();
    }

    public AppointmentDetails toAppointmentDetails() {
        return new AppointmentDetails(date, startTime, endTime, serviceName, clinicName, notes);
    }
}
