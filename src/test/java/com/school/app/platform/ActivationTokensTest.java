package com.school.app.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivationTokensTest {

    @Test
    void generateRawProducesUrlSafeUniqueTokens() {
        String first = ActivationTokens.generateRaw();
        String second = ActivationTokens.generateRaw();

        // 32 random bytes base64url-encoded without padding = 43 chars.
        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashIsDeterministicSha256Hex() {
        // Known SHA-256 test vector for "abc".
        assertThat(ActivationTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ActivationTokens.hash("abc")).isEqualTo(ActivationTokens.hash("abc"));
    }

    @Test
    void differentTokensHashDifferently() {
        assertThat(ActivationTokens.hash("token-a")).isNotEqualTo(ActivationTokens.hash("token-b"));
    }

    @Test
    void hashedTokenNeverContainsTheRawToken() {
        String raw = ActivationTokens.generateRaw();

        assertThat(ActivationTokens.hash(raw)).hasSize(64).doesNotContain(raw);
    }
}
