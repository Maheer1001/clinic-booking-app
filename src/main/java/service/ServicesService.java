package service;

import dao.*;
import model.Clinic;
import model.Service;

import java.util.List;

/**
 * Service that fetches tracking data (test results, prescriptions, appointments)
 * for a given user. Delegates to injected DAO interfaces.
 */
public class ServicesService {
    private final ClinicRepository clinicDAO;
    private final ServiceRepository serviceDAO;
    /**
     * Production constructor — uses default DAO implementations.
     */
    public ServicesService() {
        this(new dao.ClinicDAO(), new dao.ServiceDAO());
    }

    /**
     * Testable constructor — accepts any DAO implementations.
     */
    public ServicesService( ClinicRepository clinicDAO, ServiceRepository serviceDAO) {
        this.clinicDAO = clinicDAO;
        this.serviceDAO = serviceDAO;

    }

    public List<Clinic> getClinics() {
        return clinicDAO.findAll();
    }

    public List<Service> getServices(int clinicID) {
        return serviceDAO.findByClinicId(clinicID);
    }

}
