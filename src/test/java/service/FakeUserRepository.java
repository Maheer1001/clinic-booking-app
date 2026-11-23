package service;

import dao.UserRepository;
import model.AppointmentReminderDefaults;
import model.BookingCommunicationPreferences;
import model.User;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link UserRepository} for tests that must not open a database connection. */
public class FakeUserRepository implements UserRepository {

    public final Map<Integer, User> users = new HashMap<>();
    public final Map<Integer, BookingCommunicationPreferences> commPrefs = new HashMap<>();
    public final Map<Integer, Integer> defaultClinics = new HashMap<>();

    public FakeUserRepository add(User user) {
        users.put(user.userId(), user);
        return this;
    }

    private Optional<User> byEmail(String email) {
        return users.values().stream().filter(u -> u.email() != null && u.email().equalsIgnoreCase(email)).findFirst();
    }

    @Override public boolean emailExists(String email) { return byEmail(email).isPresent(); }
    @Override public boolean insertUser(String email, String passwordHash) throws SQLException {
        int id = users.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        users.put(id, new User(id, email, null, null));
        return true;
    }
    @Override public String getPasswordHashByEmail(String email) { return null; }
    @Override public boolean updatePasswordHashByEmail(String email, String passwordHash) { return false; }
    @Override public Integer getUserIdByEmail(String email) { return byEmail(email).map(User::userId).orElse(null); }
    @Override public String getEmailByUserId(int userId) { return findById(userId).map(User::email).orElse(null); }
    @Override public String getDisplayNameByUserId(int userId) { return findById(userId).map(User::getDisplayName).orElse(null); }
    @Override public Optional<User> findById(int userId) { return Optional.ofNullable(users.get(userId)); }
    @Override public void deleteUserById(int userId) { users.remove(userId); }
    @Override public int getDefaultClinic(int userId) { return defaultClinics.getOrDefault(userId, -1); }
    @Override public boolean defaultClinicExists(int userId) { return defaultClinics.containsKey(userId); }
    @Override public boolean updateDefaultClinic(int userId, int clinicId) { defaultClinics.put(userId, clinicId); return true; }
    @Override public Optional<AppointmentReminderDefaults> getDefaultAppointmentReminderTemplate(int userId) { return Optional.empty(); }
    @Override public void saveDefaultAppointmentReminderTemplate(int userId, AppointmentReminderDefaults defaults) { }
    @Override public BookingCommunicationPreferences getBookingCommunicationPreferences(int userId) {
        return commPrefs.getOrDefault(userId, BookingCommunicationPreferences.defaults());
    }
    @Override public void saveBookingCommunicationPreferences(int userId, BookingCommunicationPreferences prefs) {
        commPrefs.put(userId, prefs);
    }
}
