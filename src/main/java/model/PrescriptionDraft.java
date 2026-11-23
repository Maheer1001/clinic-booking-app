package model;

import java.time.LocalDate;

/**
 * What a clinician enters to prescribe from the "Medicine" list.
 * @param testId           optional: the test (at this clinic, for this patient) it treats
 * @param dosesPerDay      optional, 1-24
 * @param daysBetweenDoses optional, 0-365 (0 or 1 = every day)
 * @param endDate          optional, not before the start date
 */
public record PrescriptionDraft(int medicineId, Integer testId, String dosage, Integer dosesPerDay,
                                Integer daysBetweenDoses, LocalDate startDate, LocalDate endDate,
                                String instructions) {

    @Override
    public String toString() {
        return "PrescriptionDraft[<contents hidden>]";
    }
}
