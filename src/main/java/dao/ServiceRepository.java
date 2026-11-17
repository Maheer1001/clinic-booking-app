package dao;

import model.Service;

import java.util.List;

/**
 * Contract for service data access.
 */
public interface ServiceRepository {
    List<Service> findByClinicId(int clinicId);
}
