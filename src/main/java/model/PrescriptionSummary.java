package model;

import java.util.List;

public record PrescriptionSummary(String name, String dosage, int dosesPerDay, int daysBetweenDoses, String startDate, String endDate, String instructions, boolean active) implements TrackingData {

    public String getCourse() {
        String course = "";
        if (dosesPerDay == 1) {
            course = course + "Once ";
        } else if (dosesPerDay == 2) {
            course = course + "Twice ";
        } else {
            course = course + dosesPerDay + " times ";
        }
        // 0 or 1 days between doses both mean every day (0 is what the clinic records for daily courses).
        if (daysBetweenDoses <= 1) {
            course = course + "a day";
        } else {
            course = course + "every " + daysBetweenDoses + " days";
        }
        return course;
    }

    public List<String> getInstructionLines() {
        if (instructions == null || instructions.isBlank()) return List.of();
        return instructions.lines().toList();
    }
}