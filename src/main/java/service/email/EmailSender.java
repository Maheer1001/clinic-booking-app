package service.email;

import io.github.cdimascio.dotenv.Dotenv;

import javax.mail.*;
import javax.mail.internet.*;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Map;
import java.util.Properties;

/**
 * Responsible for rendering email templates and dispatching via SMTP.
 * Separated from BaseEmail so that email data classes are testable
 * without an SMTP server.
 */
public class EmailSender {

    /**
     * Hard kill-switch for environments where tests must not send real email.
     *
     * Enable via JVM property: -Demail.disabled=true
     */
    private static boolean isEmailSendingDisabled() {
        return Boolean.getBoolean("email.disabled");
    }

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    private static String getEnv(String key) {
        String v = dotenv.get(key);
        return v != null ? v : "";
    }

    private static final String SMTP_HOST = getEnv("SMTP_HOST");
    private static final String SMTP_PORT = getEnv("SMTP_PORT");
    private static final String SMTP_USERNAME = getEnv("SMTP_USERNAME");
    private static final String SMTP_PASSWORD = getEnv("SMTP_PASSWORD");
    private static final String MAIL_FROM = getEnv("MAIL_FROM");
    private static final String MAIL_FROM_NAME = getEnv("MAIL_FROM_NAME");

    private static final TemplateEngine templateEngine;
    private static final Session mailSessionStartTLS; // Port 587 with STARTTLS
    private static final Session mailSessionSSL;     // Port 465 with SSL (fallback)
    private static final boolean isEmailConfigured;
    private static final boolean isGmail; // Gmail supports both 587 and 465

    static {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("email-templates/content/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        // Validate email configuration
        isEmailConfigured = validateEmailConfig();
        isGmail = isEmailConfigured && SMTP_HOST != null && SMTP_HOST.toLowerCase().contains("gmail");

        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME != null ? SMTP_USERNAME : "", SMTP_PASSWORD != null ? SMTP_PASSWORD : "");
            }
        };

        // Create STARTTLS session (port 587) - primary method
        Properties propsStartTLS = new Properties();
        if (isEmailConfigured && !SMTP_HOST.isEmpty()) {
            propsStartTLS.put("mail.smtp.auth", "true");
            propsStartTLS.put("mail.smtp.host", SMTP_HOST);
            propsStartTLS.put("mail.smtp.port", SMTP_PORT != null && !SMTP_PORT.isEmpty() ? SMTP_PORT : "587");
            
            // TLS/SSL settings - more robust configuration
            propsStartTLS.put("mail.smtp.starttls.enable", "true");
            propsStartTLS.put("mail.smtp.starttls.required", "true");
            propsStartTLS.put("mail.smtp.starttls.protocols", "TLSv1.2 TLSv1.3");
            
            // SSL trust settings - helps with TLS handshake issues
            propsStartTLS.put("mail.smtp.ssl.trust", SMTP_HOST);
            propsStartTLS.put("mail.smtp.ssl.checkserveridentity", "true");
            
            // Timeout settings to prevent hanging
            propsStartTLS.put("mail.smtp.connectiontimeout", "10000"); // 10 seconds
            propsStartTLS.put("mail.smtp.timeout", "10000"); // 10 seconds
            propsStartTLS.put("mail.smtp.writetimeout", "10000"); // 10 seconds
            
            // Debugging (can be enabled via system property)
            if (Boolean.getBoolean("mail.debug")) {
                propsStartTLS.put("mail.debug", "true");
            }
        }
        mailSessionStartTLS = Session.getInstance(propsStartTLS, authenticator);

        // Create SSL session (port 465) - fallback for Gmail
        Properties propsSSL = new Properties();
        if (isEmailConfigured && !SMTP_HOST.isEmpty() && isGmail) {
            propsSSL.put("mail.smtp.auth", "true");
            propsSSL.put("mail.smtp.host", SMTP_HOST);
            propsSSL.put("mail.smtp.port", "465");
            
            // SSL settings (not STARTTLS)
            propsSSL.put("mail.smtp.ssl.enable", "true");
            propsSSL.put("mail.smtp.ssl.trust", SMTP_HOST);
            propsSSL.put("mail.smtp.ssl.checkserveridentity", "true");
            propsSSL.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            
            // Timeout settings
            propsSSL.put("mail.smtp.connectiontimeout", "10000");
            propsSSL.put("mail.smtp.timeout", "10000");
            propsSSL.put("mail.smtp.writetimeout", "10000");
            
            if (Boolean.getBoolean("mail.debug")) {
                propsSSL.put("mail.debug", "true");
            }
        }
        mailSessionSSL = Session.getInstance(propsSSL, authenticator);
    }

    /**
     * Validates that required email configuration is present.
     * Logs warnings for missing configuration.
     */
    private static boolean validateEmailConfig() {
        boolean valid = true;
        
        if (SMTP_HOST == null || SMTP_HOST.isEmpty()) {
            System.err.println("[Email] WARNING: SMTP_HOST not configured in .env file. Email sending will be disabled.");
            valid = false;
        }
        if (SMTP_USERNAME == null || SMTP_USERNAME.isEmpty()) {
            System.err.println("[Email] WARNING: SMTP_USERNAME not configured in .env file. Email sending will fail.");
            valid = false;
        }
        if (SMTP_PASSWORD == null || SMTP_PASSWORD.isEmpty()) {
            System.err.println("[Email] WARNING: SMTP_PASSWORD not configured in .env file. Email sending will fail.");
            valid = false;
        }
        if (MAIL_FROM == null || MAIL_FROM.isEmpty()) {
            System.err.println("[Email] WARNING: MAIL_FROM not configured in .env file. Email sending may fail.");
            valid = false;
        }
        
        if (valid) {
            System.out.println("[Email] Configuration loaded: SMTP_HOST=" + SMTP_HOST + ", PORT=" + (SMTP_PORT != null && !SMTP_PORT.isEmpty() ? SMTP_PORT : "587"));
        }
        
        return valid;
    }

    /**
     * Renders the Thymeleaf template for the given email.
     */
    public String renderHtml(Email email) {
        Context context = new Context();
        context.setVariable("recipientName", email.getRecipientName());

        Map<String, Object> templateContext = email.getTemplateContext();
        if (templateContext != null) {
            templateContext.forEach(context::setVariable);
        }

        return templateEngine.process(email.getTemplateName(), context);
    }

    /**
     * Sends the email via SMTP with retry logic and fallback strategies.
     * 
     * Strategy:
     * 1. Try STARTTLS (port 587) with retry (up to 2 attempts)
     * 2. If TLS error and Gmail, fallback to SSL (port 465)
     * 3. Provide clear error messages
     *
     * @return true if sent successfully, false otherwise
     */
    public boolean send(Email email) {
        if (isEmailSendingDisabled()) {
            // In test environments we want zero external side-effects, but we still treat it as "sent"
            // so higher-level flows can be exercised without SMTP.
            return true;
        }

        // Check if email is configured before attempting to send
        if (!isEmailConfigured) {
            System.err.println("[Email] Cannot send email: Email configuration is incomplete. Check .env file.");
            return false;
        }

        // Prepare message content once
        String htmlContent;
        try {
            htmlContent = renderHtml(email);
        } catch (RuntimeException e) {
            // A template error must not escape send(): callers (and the reminder scheduler) rely on a boolean result.
            System.err.println("[Email] Failed to render template '" + email.getTemplateName() + "': " + e.getMessage());
            return false;
        }
        MimeMessage message = createMessage(email, htmlContent);
        if (message == null) {
            return false;
        }

        // Strategy 1: Try STARTTLS (port 587) with retry
        Exception lastException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Transport.send(message);
                System.out.println("[Email] Sent to " + email.getRecipientEmail()
                        + " | Subject: " + email.getSubject() + " (STARTTLS, attempt " + attempt + ")");
                return true;
            } catch (MessagingException e) {
                lastException = e;
                String errorMsg = e.getMessage();
                
                // If it's a TLS error and we have more attempts, wait and retry
                if (attempt < 2 && isTLSRelatedError(errorMsg)) {
                    try {
                        Thread.sleep(1000 * attempt); // Exponential backoff: 1s, 2s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                
                // If TLS error and Gmail, try SSL fallback before giving up
                if (isTLSRelatedError(errorMsg) && isGmail) {
                    System.out.println("[Email] STARTTLS failed, attempting SSL fallback (port 465)...");
                    if (trySSLFallback(email, htmlContent)) {
                        return true;
                    }
                }
                
                // If not TLS error or not Gmail, break and report error
                break;
            } catch (Exception e) {
                lastException = e;
                break;
            }
        }

        // All strategies failed - report error
        if (lastException instanceof MessagingException) {
            String errorMsg = formatEmailError((MessagingException) lastException, email.getRecipientEmail());
            System.err.println("[Email] Failed to send to " + email.getRecipientEmail() + ": " + errorMsg);
        } else {
            System.err.println("[Email] Failed to send to " + email.getRecipientEmail()
                    + ": " + (lastException != null ? lastException.getMessage() : "Unknown error"));
        }
        
        if (Boolean.getBoolean("mail.debug") && lastException != null) {
            lastException.printStackTrace();
        }
        
        return false;
    }

    /**
     * Creates a MimeMessage with the given email content.
     */
    private MimeMessage createMessage(Email email, String htmlContent) {
        try {
            MimeMessage message = new MimeMessage(mailSessionStartTLS);
            String fromName = resolveFromName(email);
            message.setFrom(new InternetAddress(MAIL_FROM, fromName));
            message.setRecipient(Message.RecipientType.TO,
                    new InternetAddress(email.getRecipientEmail(), email.getRecipientName()));
            message.setSubject(email.getSubject());
            message.setContent(htmlContent, "text/html; charset=UTF-8");
            return message;
        } catch (Exception e) {
            System.err.println("[Email] Failed to create message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to send email using SSL (port 465) as a fallback.
     * Only used for Gmail when STARTTLS fails.
     */
    private boolean trySSLFallback(Email email, String htmlContent) {
        try {
            MimeMessage message = new MimeMessage(mailSessionSSL);
            String fromName = resolveFromName(email);
            message.setFrom(new InternetAddress(MAIL_FROM, fromName));
            message.setRecipient(Message.RecipientType.TO,
                    new InternetAddress(email.getRecipientEmail(), email.getRecipientName()));
            message.setSubject(email.getSubject());
            message.setContent(htmlContent, "text/html; charset=UTF-8");

            Transport.send(message);
            System.out.println("[Email] Sent to " + email.getRecipientEmail()
                    + " | Subject: " + email.getSubject() + " (SSL fallback, port 465)");
            return true;
        } catch (Exception e) {
            System.err.println("[Email] SSL fallback also failed: " + e.getMessage());
            if (Boolean.getBoolean("mail.debug")) {
                e.printStackTrace();
            }
            return false;
        }
    }

    /**
     * Checks if an error message indicates a TLS/SSL related issue.
     */
    private boolean isTLSRelatedError(String errorMsg) {
        if (errorMsg == null) return false;
        String lowerMsg = errorMsg.toLowerCase();
        return lowerMsg.contains("tls") ||
               lowerMsg.contains("ssl") ||
               lowerMsg.contains("could not convert socket") ||
               lowerMsg.contains("handshake") ||
               lowerMsg.contains("certificate");
    }

    /**
     * Formats email error messages to be more user-friendly and actionable.
     */
    private String formatEmailError(MessagingException e, String recipientEmail) {
        String message = e.getMessage();
        String className = e.getClass().getSimpleName();
        
        // Check for TLS/SSL related errors
        if (message != null) {
            String lowerMsg = message.toLowerCase();
            
            if (lowerMsg.contains("could not convert socket to tls") || 
                lowerMsg.contains("tls") || 
                lowerMsg.contains("ssl")) {
                return String.format(
                    "TLS/SSL connection failed. This is often caused by:\n" +
                    "  - Network/firewall blocking port %s or TLS connections\n" +
                    "  - Antivirus software interfering with secure connections\n" +
                    "  - Corporate/university network restrictions\n" +
                    "  - Incorrect SMTP_HOST or SMTP_PORT in .env\n" +
                    "Original error: %s",
                    SMTP_PORT != null && !SMTP_PORT.isEmpty() ? SMTP_PORT : "587",
                    message
                );
            }
            
            if (lowerMsg.contains("authentication failed") || 
                lowerMsg.contains("invalid login") ||
                lowerMsg.contains("535")) {
                return String.format(
                    "Authentication failed. Check SMTP_USERNAME and SMTP_PASSWORD in .env file.\n" +
                    "Original error: %s",
                    message
                );
            }
            
            if (lowerMsg.contains("connection") || lowerMsg.contains("timeout")) {
                return String.format(
                    "Connection failed or timed out. Check:\n" +
                    "  - SMTP_HOST (%s) and SMTP_PORT (%s) are correct\n" +
                    "  - Internet connection is active\n" +
                    "  - Firewall allows outbound connections on port %s\n" +
                    "Original error: %s",
                    SMTP_HOST,
                    SMTP_PORT != null && !SMTP_PORT.isEmpty() ? SMTP_PORT : "587",
                    SMTP_PORT != null && !SMTP_PORT.isEmpty() ? SMTP_PORT : "587",
                    message
                );
            }
            
            if (lowerMsg.contains("unknown host") || lowerMsg.contains("cannot resolve")) {
                return String.format(
                    "Cannot resolve SMTP host '%s'. Check SMTP_HOST in .env file.\n" +
                    "Original error: %s",
                    SMTP_HOST,
                    message
                );
            }
        }
        
        // Default: return the original message
        return message != null ? message : className;
    }

    /** Neutral sender name for discreet emails, so the inbox list does not reveal the clinic type. */
    static final String DISCREET_FROM_NAME = "Your Appointments";

    /**
     * Sender display name: the email's own override first, then a neutral name for discreet
     * emails, then the configured MAIL_FROM_NAME (or the clinic name).
     */
    static String resolveFromName(Email email) {
        String custom = email.getFromName();
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        if (email.isDiscreet()) {
            return DISCREET_FROM_NAME;
        }
        if (MAIL_FROM_NAME != null && !MAIL_FROM_NAME.isEmpty()) {
            return MAIL_FROM_NAME;
        }
        return "Sexual Health Clinic";
    }
}
