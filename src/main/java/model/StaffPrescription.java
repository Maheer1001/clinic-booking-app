package model;

import java.time.LocalDate;

/** A prescription as staff see it. {@code active} is "actively_taking" (false once stopped). */
public record StaffPrescription(int prescriptionId, int patientUserId, int clinicId, int medicineId,
                                String medicineName, Integer testId, String dosage, Integer dosesPerDay,
                                Integer daysBetweenDoses, LocalDate startDate, LocalDate endDate,
                                String instructions, boolean active, Integer prescribingClinicianId) {

    @Override
    public String toString() {
        return "StaffPrescription[prescriptionId=" + prescriptionId + ", <contents hidden>]";
    }
}
