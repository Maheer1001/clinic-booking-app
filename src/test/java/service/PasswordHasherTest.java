package service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hash_thenVerify_roundTrips() {
        String stored = PasswordHasher.hash("Password1");
        assertTrue(PasswordHasher.verify("Password1", stored));
        assertFalse(PasswordHasher.verify("Password2", stored));
        assertFalse(PasswordHasher.verify("password1", stored));
    }

    @Test
    void hash_isSalted() {
        assertNotEquals(PasswordHasher.hash("Password1"), PasswordHasher.hash("Password1"));
    }

    @Test
    void verify_acceptsLegacySha256Hex() {
        String legacy = PasswordHasher.legacySha256Hex("Password1");
        assertEquals(64, legacy.length());
        assertTrue(PasswordHasher.verify("Password1", legacy));
        assertTrue(PasswordHasher.verify("Password1", legacy.toUpperCase()));
        assertFalse(PasswordHasher.verify("Password2", legacy));
    }

    @Test
    void needsRehash_onlyForLegacyOrWeakerHashes() {
        assertTrue(PasswordHasher.needsRehash(PasswordHasher.legacySha256Hex("x")));
        assertTrue(PasswordHasher.needsRehash(null));
        assertTrue(PasswordHasher.needsRehash("pbkdf2$1000$AAAA$AAAA"));
        assertFalse(PasswordHasher.needsRehash(PasswordHasher.hash("Password1")));
    }

    @Test
    void verify_rejectsMalformedOrMissingHashes() {
        assertFalse(PasswordHasher.verify("Password1", null));
        assertFalse(PasswordHasher.verify("Password1", ""));
        assertFalse(PasswordHasher.verify("Password1", "pbkdf2$abc$def"));
        assertFalse(PasswordHasher.verify("Password1", "pbkdf2$x$y$z"));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("Password1")));
    }

    @Test
    void storedHash_fitsInPasswordHashColumn() {
        assertTrue(PasswordHasher.hash("a-long-password-123456789").length() <= 255);
    }
}
