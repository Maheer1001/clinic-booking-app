package dao;

import model.Prescription;
import model.PrescriptionSummary;

import java.util.List;

/**
 * Contract for prescription data access.
 */
public interface PrescriptionRepository {
    List<PrescriptionSummary> getPrescriptionsByTestId(int testId);
    List<PrescriptionSummary> getPrescriptionsByUserId(int userId);
    List<Prescription> getPrescriptionsForRemindersByUserId(int userId);
    String getMedicineByPrescriptionId(int prescriptionId);
}
