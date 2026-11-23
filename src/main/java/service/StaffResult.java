package service;

/**
 * Outcome of a {@link StaffService} call: a status, the value on success (or extra detail, e.g. for
 * NEEDS_CONFIRMATION) and a message ready to show the user.
 */
public record StaffResult<T>(Status status, T value, String message) {

    public enum Status {
        OK(null),
        NOT_STAFF("This account doesn't have staff access."),
        NOT_AT_CLINIC("You don't have access to this clinic."),
        ADMIN_ONLY("Only a clinic admin can do this."),
        /** Also used for a patient or record that exists but is not visible at this clinic. */
        NOT_FOUND("We couldn't find that at this clinic."),
        INVALID("Please check the details and try again."),
        /** Nothing was saved: ask the user to confirm, then call again with confirmation. */
        NEEDS_CONFIRMATION("Please confirm before continuing."),
        DB_ERROR("We couldn't reach the server. Please try again.");

        private final String defaultMessage;

        Status(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

        public String defaultMessage() {
            return defaultMessage;
        }
    }

    public boolean ok() {
        return status == Status.OK;
    }

    public static <T> StaffResult<T> ok(T value) {
        return new StaffResult<>(Status.OK, value, null);
    }

    public static <T> StaffResult<T> ok(T value, String message) {
        return new StaffResult<>(Status.OK, value, message);
    }

    public static <T> StaffResult<T> fail(Status status) {
        return new StaffResult<>(status, null, status.defaultMessage());
    }

    public static <T> StaffResult<T> invalid(String message) {
        return new StaffResult<>(Status.INVALID, null, message);
    }

    public static <T> StaffResult<T> confirm(T detail, String message) {
        return new StaffResult<>(Status.NEEDS_CONFIRMATION, detail, message);
    }
}
