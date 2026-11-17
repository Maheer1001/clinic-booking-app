package service.email;

/**
 * Abstract base class for all email data objects.
 * Holds recipient info and delegates content to subclasses.
 * Rendering and sending are handled by {@link EmailSender}.
 */
public abstract class BaseEmail implements Email {

    protected final String recipientEmail;
    protected final String recipientName;

    protected BaseEmail(String recipientEmail, String recipientName) {
        this.recipientEmail = recipientEmail;
        this.recipientName = recipientName;
    }

    @Override
    public String getRecipientEmail() {
        return recipientEmail;
    }

    @Override
    public String getRecipientName() {
        return recipientName;
    }
}
