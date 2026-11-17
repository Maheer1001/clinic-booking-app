package dao;

import model.Clinic;

import java.util.List;

/**
 * Contract for clinic data access.
 */
public interface ClinicRepository {
    List<Clinic> findAll();
    Clinic findById(int clinicId);
}
