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


        if (dob == null || dob.isAfter(LocalDate.now())) {
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

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}