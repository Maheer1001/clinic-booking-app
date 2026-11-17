package service;

import dao.AppointmentRepository;
import dao.AppointmentDAO;
import dao.PrescriptionRepository;
import dao.PrescriptionDAO;
import dao.TestRepository;
import dao.TestDAO;
import model.AppointmentDetails;
import model.PrescriptionSummary;
import model.Test;

import java.util.List;

/**
 * Service that fetches tracking data (test results, prescriptions, appointments)
 * for a given user. Delegates to injected DAO interfaces.
 */
public class TrackingService {
    private final int userId;
    private final TestRepository testDAO;
    private final PrescriptionRepository prescriptionDAO;
    private final AppointmentRepository appointmentDAO;

    /**
     * Production constructor — uses default DAO implementations.
     */
    public TrackingService(int userId) {
        this(userId, new TestDAO(), new PrescriptionDAO(), new AppointmentDAO());
    }

    /**
     * Testable constructor — accepts any DAO implementations.
     */
    public TrackingService(int userId, TestRepository testDAO,
                           PrescriptionRepository prescriptionDAO,
                           AppointmentRepository appointmentDAO) {
        this.userId = userId;
        this.testDAO = testDAO;
        this.prescriptionDAO = prescriptionDAO;
        this.appointmentDAO = appointmentDAO;
    }

    public List<Test> getTestResults() {
        return testDAO.getTestsByUserId(userId);
    }

    public List<PrescriptionSummary> getPrescriptions() {
        return prescriptionDAO.getPrescriptionsByUserId(userId);
    }

    public List<AppointmentDetails> getPastAppointments() {
        return appointmentDAO.getPastAppointmentDetailsByUserId(userId);
    }
}
