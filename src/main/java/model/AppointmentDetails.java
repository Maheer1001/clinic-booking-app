package model;

import java.util.List;

public record AppointmentDetails(String date, String startTime, String endTime, String serviceName, String clinicName, String notes) implements TrackingData {

    public List<String> getNotesLines() {
        if (notes == null) { return List.of(); } // Prevents NPE when notes is null.
        return notes.lines().toList();
    }
}