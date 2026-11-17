package service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Salted PBKDF2 password hashing.
 *
 * Stored format: {@code pbkdf2$<iterations>$<base64 salt>$<base64 hash>}.
 * Hashes created before this class existed are unsalted SHA-256 hex strings; they are still
 * accepted by {@link #verify} and reported by {@link #needsRehash} so they can be upgraded
 * the next time the user logs in.
 */
public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RNG.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    /** Constant-time check of a password against a stored hash (PBKDF2 or legacy SHA-256). */
    public static boolean verify(String password, String stored) {
        if (password == null || stored == null || stored.isEmpty()) return false;

        if (stored.startsWith(PREFIX + "$")) {
            String[] parts = stored.split("\\$");
            if (parts.length != 4) return false;
            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                if (iterations <= 0 || expected.length == 0) return false;
                byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
                return MessageDigest.isEqual(expected, actual);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        byte[] expected = stored.toLowerCase().getBytes(StandardCharsets.US_ASCII);
        byte[] actual = legacySha256Hex(password).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    /** True when the stored hash should be replaced with a fresh one (legacy or weaker parameters). */
    public static boolean needsRehash(String stored) {
        if (stored == null || !stored.startsWith(PREFIX + "$")) return true;
        String[] parts = stored.split("\\$");
        if (parts.length != 4) return true;
        try {
            return Integer.parseInt(parts[1]) < ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** Unsalted SHA-256 hex, the scheme used before PBKDF2. Only for verifying old hashes. */
    static String legacySha256Hex(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        return pbkdf2(password, salt, iterations, KEY_BITS);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations, int keyBits) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, keyBits);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " not available", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
