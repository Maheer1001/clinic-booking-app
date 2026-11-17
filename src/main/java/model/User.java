package model;

/**
 * Domain model for a user.
 */
public record User(int userId, String email, String firstName, String lastName) {

    public User(int userId, String email, String firstName, String lastName) {
        this.userId = userId;
        this.email = email != null ? email : "";
        this.firstName = firstName != null ? firstName : "";
        this.lastName = lastName != null ? lastName : "";
    }

    /**
     * Returns display name (first + last), or email if no name set.
     */
    public String getDisplayName() {
        if (!firstName.isBlank() && !lastName.isBlank()) {
            return firstName.trim() + " " + lastName.trim();
        }
        if (!firstName.isBlank()) return firstName.trim();
        if (!lastName.isBlank()) return lastName.trim();
        return !email.isBlank() ? email : "";
    }
}
