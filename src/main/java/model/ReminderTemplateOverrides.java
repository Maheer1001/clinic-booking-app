package model;

/**
 * Structured overrides for reminder email + in-app notification content,
 * stored as a JSON string in the existing customMessage field.

 * All fields are optional. When a field is null/blank, callers should
 * fall back to their own defaults.
 */
public class ReminderTemplateOverrides {

    private final String emailSubject;
    private final String emailBodyIntro;
    private final String emailFromName;
    private final String notificationTitle;
    private final String notificationPreview;
    private final String notificationBody;
    // null = follow global setting, true/false = explicit override
    private final Boolean discreetOverride;

    public ReminderTemplateOverrides(String emailSubject,
                                     String emailBodyIntro,
                                     String emailFromName,
                                     String notificationTitle,
                                     String notificationPreview,
                                     String notificationBody,
                                     Boolean discreetOverride) {
        this.emailSubject = trimToNull(emailSubject);
        this.emailBodyIntro = trimToNull(emailBodyIntro);
        this.emailFromName = trimToNull(emailFromName);
        this.notificationTitle = trimToNull(notificationTitle);
        this.notificationPreview = trimToNull(notificationPreview);
        this.notificationBody = trimToNull(notificationBody);
        this.discreetOverride = discreetOverride;
    }

    public String getEmailSubject() {
        return emailSubject;
    }

    public String getEmailBodyIntro() {
        return emailBodyIntro;
    }

    public String getEmailFromName() {
        return emailFromName;
    }

    public String getNotificationTitle() {
        return notificationTitle;
    }

    public String getNotificationPreview() {
        return notificationPreview;
    }

    public String getNotificationBody() {
        return notificationBody;
    }

    public Boolean getDiscreetOverride() {
        return discreetOverride;
    }

    /**
     * Serialises non-null fields into a simple JSON object string.
     * If all fields are null, returns an empty string.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;

        first = appendField(sb, "emailSubject", emailSubject, first);
        first = appendField(sb, "emailBodyIntro", emailBodyIntro, first);
        first = appendField(sb, "emailFromName", emailFromName, first);
        first = appendField(sb, "notificationTitle", notificationTitle, first);
        first = appendField(sb, "notificationPreview", notificationPreview, first);
        first = appendField(sb, "notificationBody", notificationBody, first);
        if (discreetOverride != null) {
            first = appendField(sb, "discreet", discreetOverride.toString(), first);
        }

        sb.append('}');
        String json = sb.toString();
        // If object is empty ({}) treat as no overrides.
        return "{}".equals(json) ? "" : json;
    }

    private static boolean appendField(StringBuilder sb, String key, String value, boolean first) {
        if (value == null) {
            return first;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(escape(key)).append('"').append(':');
        sb.append('"').append(escape(value)).append('"');
        return false;
    }

    private static String escape(String input) {
        StringBuilder out = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String unescape(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                switch (next) {
                    case '\\' -> {
                        out.append('\\');
                        i++;
                    }
                    case '"' -> {
                        out.append('"');
                        i++;
                    }
                    case 'n' -> {
                        out.append('\n');
                        i++;
                    }
                    case 'r' -> {
                        out.append('\r');
                        i++;
                    }
                    case 't' -> {
                        out.append('\t');
                        i++;
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Parses a JSON string previously produced by {@link #toJson()}.
     * If the input is null/blank, returns an instance with all fields null.
     * If parsing fails or the string is not JSON, treats the whole string
     * as a legacy free-form message and maps it to emailBodyIntro + notificationBody.
     */
    public static ReminderTemplateOverrides fromString(String raw) {
        if (raw == null) {
            return new ReminderTemplateOverrides(null, null, null, null, null, null, null);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new ReminderTemplateOverrides(null, null, null, null, null, null, null);
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            // Legacy plain text — use as generic body/intro.
            return new ReminderTemplateOverrides(null, trimmed, null, null, null, trimmed, null);
        }

        try {
            return parseJsonObject(trimmed);
        } catch (Exception e) {
            // On any parse error, fall back to legacy behaviour.
            return new ReminderTemplateOverrides(null, trimmed, null, null, null, trimmed, null);
        }
    }

    private static ReminderTemplateOverrides parseJsonObject(String json) {
        String emailSubject = null;
        String emailBodyIntro = null;
        String emailFromName = null;
        String notificationTitle = null;
        String notificationPreview = null;
        String notificationBody = null;
        Boolean discreetOverride = null;

        int i = 1; // skip '{'
        int len = json.length();
        while (i < len - 1) { // stop before final '}'
            // Skip whitespace and commas
            char c = json.charAt(i);
            while (i < len - 1 && (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ',')) {
                i++;
                c = json.charAt(i);
            }
            if (c != '"') {
                break;
            }
            // Parse key
            int keyStart = ++i;
            StringBuilder keyBuf = new StringBuilder();
            boolean inEscape = false;
            while (i < len) {
                char ch = json.charAt(i);
                if (inEscape) {
                    keyBuf.append(ch);
                    inEscape = false;
                } else if (ch == '\\') {
                    inEscape = true;
                } else if (ch == '"') {
                    break;
                } else {
                    keyBuf.append(ch);
                }
                i++;
            }
            String key = unescape(keyBuf.toString());
            // Move past closing quote
            i++;
            // Skip whitespace and colon
            while (i < len && (json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t' || json.charAt(i) == ':')) {
                i++;
            }
            if (i >= len || json.charAt(i) != '"') {
                break;
            }
            // Parse value
            i++; // skip opening quote
            StringBuilder valBuf = new StringBuilder();
            inEscape = false;
            while (i < len) {
                char ch = json.charAt(i);
                if (inEscape) {
                    valBuf.append(ch);
                    inEscape = false;
                } else if (ch == '\\') {
                    inEscape = true;
                } else if (ch == '"') {
                    break;
                } else {
                    valBuf.append(ch);
                }
                i++;
            }
            String value = unescape(valBuf.toString());
            // Move past closing quote
            i++;

            switch (key) {
                case "emailSubject" -> emailSubject = trimToNull(value);
                case "emailBodyIntro" -> emailBodyIntro = trimToNull(value);
                case "emailFromName" -> emailFromName = trimToNull(value);
                case "notificationTitle" -> notificationTitle = trimToNull(value);
                case "notificationPreview" -> notificationPreview = trimToNull(value);
                case "notificationBody" -> notificationBody = trimToNull(value);
                case "discreet" -> {
                    String v = trimToNull(value);
                    if (v != null) {
                        discreetOverride = Boolean.valueOf(v);
                    }
                }
                default -> {
                    // ignore unknown keys
                }
            }
        }

        if (discreetOverride == null) {
            discreetOverride = Boolean.TRUE;
        }

        return new ReminderTemplateOverrides(emailSubject, emailBodyIntro, emailFromName,
                notificationTitle, notificationPreview, notificationBody, discreetOverride);
    }
}

