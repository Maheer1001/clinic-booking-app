package model;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured scheduling options for appointment reminders,
 * stored as JSON in the {@code custom_days} column.
 * {
 *   "reminders": [
 *     {
 *       "amount": 1,
 *       "unit": "day",
 *       "timeOfDay": "09:00",
 *       "repeatEvery": { "amount": 1, "unit": "day" }
 *     }
 *   ],
 *   "quietHours": { "start": "22:00", "end": "07:00" }
 * }
 * Parsing is tolerant and will fall back to simple "X days before" behaviour
 * when given a plain integer string.
 */
public class ReminderSchedule {

    /**
     * @param unit "second" | "minute" | "hour" | "day" | "week"
     */
    public record RepeatEvery(int amount, String unit) {
    }

    /**
     * @param unit        "second" | "minute" | "hour" | "day" | "week"
     * @param timeOfDay   "HH:mm" or null
     * @param repeatEvery nullable
     */
    public record ReminderRule(int amount, String unit, String timeOfDay, RepeatEvery repeatEvery) {
    }

    /**
     * @param start "HH:mm"
     * @param end   "HH:mm"
     */
    public record QuietHours(String start, String end) {
    }

    private final List<ReminderRule> reminders;
    private final QuietHours quietHours;

    public ReminderSchedule(List<ReminderRule> reminders, QuietHours quietHours) {
        this.reminders = reminders != null ? reminders : new ArrayList<>();
        this.quietHours = quietHours;
    }

    public List<ReminderRule> getReminders() {
        return reminders;
    }

    public QuietHours getQuietHours() {
        return quietHours;
    }

    /**
     * Serialise to JSON string matching the documented schema.
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean firstTop = true;

        // reminders array
        sb.append("\"reminders\":[");
        boolean firstRem = true;
        for (ReminderRule r : reminders) {
            if (!firstRem) {
                sb.append(',');
            }
            firstRem = false;
            sb.append('{');
            sb.append("\"amount\":").append(r.amount);
            sb.append(",\"unit\":\"").append(escape(r.unit)).append('"');
            if (r.timeOfDay != null && !r.timeOfDay.isBlank()) {
                sb.append(",\"timeOfDay\":\"").append(escape(r.timeOfDay.trim())).append('"');
            }
            if (r.repeatEvery != null && r.repeatEvery.amount > 0 && r.repeatEvery.unit != null) {
                sb.append(",\"repeatEvery\":{");
                sb.append("\"amount\":").append(r.repeatEvery.amount);
                sb.append(",\"unit\":\"").append(escape(r.repeatEvery.unit)).append('"');
                sb.append('}');
            }
            sb.append('}');
        }
        sb.append(']');
        firstTop = false;

        if (quietHours != null && quietHours.start != null && quietHours.end != null) {
            sb.append(',');
            sb.append("\"quietHours\":{");
            sb.append("\"start\":\"").append(escape(quietHours.start)).append('"');
            sb.append(",\"end\":\"").append(escape(quietHours.end)).append('"');
            sb.append('}');
        }

        sb.append('}');
        return sb.toString();
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

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Parses a schedule from the stored custom_days string.
     * Behaviour:
     *  - null/blank -> default single reminder with given defaultDaysBefore.
     *  - plain integer string -> single reminder defaultDaysBefore = parsed value.
     *  - JSON object -> parsed according to schema above.
     */
    public static ReminderSchedule fromString(String raw, int defaultDaysBefore) {
        if (raw == null) {
            return defaultSingle(defaultDaysBefore);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return defaultSingle(defaultDaysBefore);
        }

        // Plain integer = simple "X days before"
        if (!trimmed.startsWith("{")) {
            try {
                int days = Integer.parseInt(trimmed);
                return defaultSingle(days);
            } catch (NumberFormatException ignored) {
                return defaultSingle(defaultDaysBefore);
            }
        }

        try {
            return parseJsonObject(trimmed, defaultDaysBefore);
        } catch (Exception e) {
            return defaultSingle(defaultDaysBefore);
        }
    }

    private static ReminderSchedule defaultSingle(int days) {
        if (days <= 0) {
            days = 2;
        }
        List<ReminderRule> list = new ArrayList<>();
        list.add(new ReminderRule(days, "day", null, null));
        return new ReminderSchedule(list, null);
    }

    private static ReminderSchedule parseJsonObject(String json, int defaultDaysBefore) {
        List<ReminderRule> rules = new ArrayList<>();
        QuietHours quiet = null;

        int len = json.length();
        int i = json.indexOf("\"reminders\"");
        if (i >= 0) {
            int arrayStart = json.indexOf('[', i);
            int arrayEnd = json.indexOf(']', arrayStart);
            if (arrayStart > 0 && arrayEnd > arrayStart) {
                String arr = json.substring(arrayStart + 1, arrayEnd);
                int pos = 0;
                while (pos < arr.length()) {
                    int objStart = arr.indexOf('{', pos);
                    if (objStart < 0) break;
                    int objEnd = findMatchingBrace(arr, objStart);
                    if (objEnd < 0) break;
                    String obj = arr.substring(objStart, objEnd + 1);
                    ReminderRule r = parseReminderRule(obj, defaultDaysBefore);
                    if (r != null) {
                        rules.add(r);
                    }
                    pos = objEnd + 1;
                }
            }
        }

        int qIndex = json.indexOf("\"quietHours\"");
        if (qIndex >= 0) {
            int objStart = json.indexOf('{', qIndex);
            int objEnd = findMatchingBrace(json, objStart);
            if (objStart > 0 && objEnd > objStart) {
                String obj = json.substring(objStart, objEnd + 1);
                String start = extractStringField(obj, "start");
                String end = extractStringField(obj, "end");
                if (start != null && end != null) {
                    quiet = new QuietHours(start, end);
                }
            }
        }

        if (rules.isEmpty()) {
            return defaultSingle(defaultDaysBefore);
        }
        return new ReminderSchedule(rules, quiet);
    }

    private static int findMatchingBrace(String s, int openIndex) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static ReminderRule parseReminderRule(String json, int defaultDaysBefore) {
        int amount = defaultDaysBefore;
        String unit = "day";
        String timeOfDay = null;
        RepeatEvery repeat = null;

        String amountStr = extractNumberField(json, "amount");
        if (amountStr != null) {
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException ignored) {
            }
        }
        String unitStr = extractStringField(json, "unit");
        if (unitStr != null) {
            unit = unitStr;
        }
        timeOfDay = trimToNull(extractStringField(json, "timeOfDay"));

        int repIndex = json.indexOf("\"repeatEvery\"");
        if (repIndex >= 0) {
            int objStart = json.indexOf('{', repIndex);
            int objEnd = findMatchingBrace(json, objStart);
            if (objStart > 0 && objEnd > objStart) {
                String repObj = json.substring(objStart, objEnd + 1);
                String rAmt = extractNumberField(repObj, "amount");
                String rUnit = extractStringField(repObj, "unit");
                if (rAmt != null && rUnit != null) {
                    try {
                        int ra = Integer.parseInt(rAmt);
                        if (ra > 0) {
                            repeat = new RepeatEvery(ra, rUnit);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return new ReminderRule(amount, unit, timeOfDay, repeat);
    }

    private static String extractStringField(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int quoteStart = json.indexOf('"', colon);
        if (quoteStart < 0) return null;
        int i = quoteStart + 1;
        StringBuilder val = new StringBuilder();
        boolean escape = false;
        for (; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                val.append(c);
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') break;
            val.append(c);
        }
        return val.toString();
    }

    private static String extractNumberField(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return null;
        int i = colon + 1;
        StringBuilder val = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                val.append(c);
            } else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                if (!val.isEmpty()) break;
            } else {
                if (!val.isEmpty()) break;
            }
            i++;
        }
        return val.isEmpty() ? null : val.toString();
    }

    private static final DateTimeFormatter TIME_24H = DateTimeFormatter.ofPattern("H:mm");

    /**
     * Reads a 24-hour time typed by the user. Accepts "9:00" as well as "09:00" (the old "HH:mm"
     * pattern rejected single-digit hours, so such times were silently ignored).
     *
     * @return the time, or null when blank or not a valid time
     */
    public static LocalTime parseTime(String text) {
        String t = trimToNull(text);
        if (t == null) return null;
        try {
            return LocalTime.parse(t, TIME_24H);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Utility to snap a candidate send time to be outside quiet hours, if defined.
     */
    public LocalDateTime applyQuietHours(LocalDateTime candidate) {
        if (quietHours == null || quietHours.start == null || quietHours.end == null) {
            return candidate;
        }
        LocalTime start = parseTime(quietHours.start);
        LocalTime end = parseTime(quietHours.end);
        if (start == null || end == null || start.equals(end)) {
            return candidate;
        }
        try {
            LocalTime t = candidate.toLocalTime();

            boolean wraps = start.isAfter(end); // e.g. 22:00 -> 07:00

            if (!wraps) {
                if (!t.isBefore(start) && t.isBefore(end)) {
                    // inside [start, end)
                    return candidate.with(end);
                }
            } else {
                // Quiet from start to midnight and midnight to end.
                if (!t.isBefore(start) || t.isBefore(end)) {
                    if (!t.isBefore(start)) {
                        // same day, move to next day at end
                        return candidate.plusDays(1).with(end);
                    } else {
                        // before end on same day
                        return candidate.with(end);
                    }
                }
            }
        } catch (Exception ignored) {
            return candidate;
        }
        return candidate;
    }
}

