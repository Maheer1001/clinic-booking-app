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
     * @return the new user id
     */
    int createStaffUser(String email, String passwordHash, StaffRole role, int clinicId,
                        boolean mustResetPassword) throws SQLException;
}
