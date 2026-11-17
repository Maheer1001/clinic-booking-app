package service.email;

import java.util.Map;

/**
 * Contract for all email types in the sexual health application.
 * Each implementation defines its own subject, template, and context data.
 */
public interface Email {

    /**
     * @return the subject line of the email
     */
    String getSubject();

    /**
     * @return the Thymeleaf template name (without extension) under email-templates/content/
     */
    String getTemplateName();

    /**
     * @return context variables to be injected into the Thymeleaf template
     */
    Map<String, Object> getTemplateContext();

    /**
     * @return the recipient email address
     */
    String getRecipientEmail();

    /**
     * @return the recipient's display name
     */
    String getRecipientName();

    /**
     * Optional display name for the sender (\"From\" field).
     * If null or blank, the global default configured in EmailSender is used.
     */
    default String getFromName() {
        return null;
    }

    /**
     * True when the email must not reveal sensitive details (service type, clinic, treatment,
     * clinic branding in the sender name). Discreet emails get a neutral sender name and header.
     */
    default boolean isDiscreet() {
        return false;
    }
}
