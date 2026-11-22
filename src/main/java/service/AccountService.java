package service;

import dao.UserDAO;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.regex.Pattern;

public class AccountService {

    public static class AccountInfo {
        public final String firstName;
        public final String lastName;
        public final String phone;
        public final LocalDate dob;
        public final String email;

        public AccountInfo(String firstName, String lastName, String phone, LocalDate dob, String email) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.phone = phone;
            this.dob = dob;
            this.email = email;
        }
    }

    public enum SaveResult {
        SUCCESS,
        INVALID_EMAIL,
        EMAIL_ALREADY_USED,
        INVALID_NAME,
        INVALID_PHONE,
        INVALID_DOB,
        DB_ERROR
    }

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserDAO userDAO = new UserDAO();

    public AccountInfo loadAccount(int userId) {
        try {
            return userDAO.getAccountInfo(userId);
        } catch (SQLException e) {
            return null;
        }
    }

    public SaveResult saveAccount(int userId,
                                  String firstName,
                                  String lastName,
                                  String phone,
                                  LocalDate dob,
                                  String email) {


        if (email == null || !EMAIL_PATTERN.matcher(email.trim().toLowerCase()).matches()) {
            return SaveResult.INVALID_EMAIL;
        }


        // Phone is optional; if provided, require exactly 11 digits (UI enforces this too).
        if (phone != null && !phone.matches("\\d{11}")) {
            return SaveResult.INVALID_PHONE;
        }


        // first_name / last_name are VARCHAR(50); longer values used to fail as a generic DB error.
        if (tooLong(firstName) || tooLong(lastName)) {
            return SaveResult.INVALID_NAME;
        }

        // Date of birth is optional (saved as NULL); one that is given must be plausible.
        if (dob != null && !isValidDob(dob)) {
            return SaveResult.INVALID_DOB;
        }

        try {
            String em = email.trim().toLowerCase();

            if (userDAO.emailUsedByOtherUser(userId, em)) {
                return SaveResult.EMAIL_ALREADY_USED;
            }

            boolean ok = userDAO.updateAccountInfo(userId,
                    trim(firstName),
                    trim(lastName),
                    trim(phone),
                    dob,
                    em);

            return ok ? SaveResult.SUCCESS : SaveResult.DB_ERROR;
        } catch (SQLException e) {
            return SaveResult.DB_ERROR;
        }
    }

    public static final int MAX_NAME_LENGTH = 50;
    /** Oldest date of birth accepted; anything earlier is almost certainly a typo. */
    public static final int MAX_AGE_YEARS = 120;

    public static boolean isValidDob(LocalDate dob) {
        if (dob == null) return false;
        LocalDate today = LocalDate.now();
        return !dob.isAfter(today) && !dob.isBefore(today.minusYears(MAX_AGE_YEARS));
    }

    private static boolean tooLong(String name) {
        return name != null && name.trim().length() > MAX_NAME_LENGTH;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}