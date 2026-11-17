package service.email;

import java.time.Duration;
import java.util.Map;

public class PasswordResetOtpEmail extends BaseEmail {

    private final String code;
    private final Duration ttl;

    public PasswordResetOtpEmail(String recipientEmail, String recipientName, String code, Duration ttl) {
        super(recipientEmail, recipientName);
        this.code = code;
        this.ttl = ttl;
    }

    @Override
    public String getSubject() {
        return "Password reset code";
    }

    @Override
    public String getTemplateName() {
        return "password-reset-otp";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        long minutes = ttl != null ? Math.max(1, ttl.toMinutes()) : 10;
        return Map.of(
                "otpCode", code,
                "otpMinutes", minutes
        );
    }
}

