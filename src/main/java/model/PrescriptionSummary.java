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
        if (daysBetweenDoses == 1) {
            course = course + "everyday";
        } else {
            course = course + "every " + daysBetweenDoses + " days";
        }
        return course;
    }

    public List<String> getInstructionLines() {
        return instructions.lines().toList();
    }
}