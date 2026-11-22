package model;

import java.util.List;

public record PrescriptionSummary(String name, String dosage, int dosesPerDay, int daysBetweenDoses, String startDate, String endDate, String instructions, boolean active) implements TrackingData {

    public String getCourse() {
        // No doses per day recorded (NULL reads as 0): there is no schedule to state, so don't print "0 times a day".
        if (dosesPerDay <= 0) {
            return "As directed";
        }
        // 0 or 1 days between doses both mean every day (0 is what the clinic records for daily courses).
        boolean daily = daysBetweenDoses <= 1;
        if (dosesPerDay == 1) {
            return daily ? "Once a day" : "Once every " + daysBetweenDoses + " days";
        }
        String times = dosesPerDay == 2 ? "Twice" : dosesPerDay + " times";
        // doses_per_day is per dosing day, so 3 doses every 2 days is "3 times a day, every 2 days"
        // (it used to read "3 times every 2 days", i.e. three doses spread over two days).
        return daily ? times + " a day" : times + " a day, every " + daysBetweenDoses + " days";
    }

    public List<String> getInstructionLines() {
        if (instructions == null || instructions.isBlank()) return List.of();
        return instructions.lines().toList();
    }
}