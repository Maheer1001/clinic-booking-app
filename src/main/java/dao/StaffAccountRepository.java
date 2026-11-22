package dao;

import model.StaffRole;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Data access for staff sign-up: hashed clinic staff codes, the wrong-code limit and creating staff accounts. */
public interface StaffAccountRepository {

    /** One clinic's hashed staff code. */
    record ClinicCodeHash(int clinicId, String codeHash) {}

    List<ClinicCodeHash> allStaffCodeHashes() throws SQLException;

    /** Wrong codes entered for this email hash at or after {@code since}. */
    int failedCodeAttempts(String emailHash, LocalDateTime since) throws SQLException;

    /** Wrong codes entered for any email at or after {@code since}. */
    int failedCodeAttemptsAll(LocalDateTime since) throws SQLException;

    void recordFailedCodeAttempt(String emailHash, LocalDateTime at) throws SQLException;

    void clearFailedCodeAttempts(String emailHash) throws SQLException;

    boolean emailExists(String email) throws SQLException;

    /**
     * Creates the account with the role, its clinic membership and an audit row, in one transaction.
     * The membership is ACTIVE (used for the seeded first admin and in tests).
     * @return the new user id
     */
    default int createStaffUser(String email, String passwordHash, StaffRole role, int clinicId,
                                boolean mustResetPassword) throws SQLException {
        return createStaffUser(email, passwordHash, role, clinicId, mustResetPassword, false);
    }

    /**
     * As above; {@code pending} creates the membership PENDING: the account can log in but sees no
     * clinic until a clinic admin approves it (staff sign-up with a clinic code).
     */
    int createStaffUser(String email, String passwordHash, StaffRole role, int clinicId,
                        boolean mustResetPassword, boolean pending) throws SQLException;
}
