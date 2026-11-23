package model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The fixed list of security questions a user can choose from. {@link #key()} is what is stored
 * ("User".security_question); {@link #prompt()} is the text to show.
 */
public enum SecurityQuestion {
    MOTHERS_MAIDEN_NAME("What is your mother's maiden name?"),
    FIRST_JOB("What was your first job?"),
    CHILDHOOD_TOWN("In which town or city did you grow up?"),
    FIRST_SECONDARY_TEACHER("What was the name of your first secondary school teacher?"),
    FIRST_PET("What was the name of your first pet?"),
    CHILDHOOD_BEST_FRIEND("What was the first name of your childhood best friend?"),
    FIRST_CONCERT("What was the first concert or gig you went to?");

    private final String prompt;

    SecurityQuestion(String prompt) {
        this.prompt = prompt;
    }

    public String key() {
        return name();
    }

    public String prompt() {
        return prompt;
    }

    /** All questions in display order. */
    public static List<SecurityQuestion> all() {
        return List.of(values());
    }

    /** The question with this key (case-insensitive), or empty for null/unknown keys. */
    public static Optional<SecurityQuestion> fromKey(String key) {
        if (key == null) return Optional.empty();
        String k = key.strip();
        return Arrays.stream(values()).filter(q -> q.name().equalsIgnoreCase(k)).findFirst();
    }
}
