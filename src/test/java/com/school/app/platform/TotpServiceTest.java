package com.school.app.platform;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void generateSecretProducesNonBlankUniqueSecrets() {
        String first = totpService.generateSecret();
        String second = totpService.generateSecret();

        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void otpAuthUriContainsSecretAndEncodedIssuerLabel() {
        String uri = totpService.otpAuthUri("MYSECRET", "operator@school.app");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=MYSECRET");
        assertThat(uri).contains("issuer=SchoolApp+Operator");
        // The label is "<issuer>:<email>" URL-encoded — the raw colon and space must not appear.
        assertThat(uri).contains("SchoolApp+Operator%3Aoperator%40school.app");
    }

    @Test
    void verifyAcceptsACurrentlyValidCode() throws CodeGenerationException {
        String secret = totpService.generateSecret();
        long counter = new SystemTimeProvider().getTime() / 30;
        String code = new DefaultCodeGenerator().generate(secret, counter);

        assertThat(totpService.verify(secret, code)).isTrue();
    }

    @Test
    void verifyRejectsAWrongCode() throws CodeGenerationException {
        String secret = totpService.generateSecret();
        long counter = new SystemTimeProvider().getTime() / 30;
        String valid = new DefaultCodeGenerator().generate(secret, counter);
        // Guaranteed different from the currently valid code (and from its drift-window neighbours
        // only with overwhelming probability — acceptable for a 1-in-10^6 collision).
        String wrong = String.format("%06d", (Integer.parseInt(valid) + 1) % 1_000_000);

        assertThat(totpService.verify(secret, wrong)).isFalse();
    }

    @Test
    void verifyRejectsNullOrBlankInputs() {
        assertThat(totpService.verify(null, "123456")).isFalse();
        assertThat(totpService.verify("SECRET", null)).isFalse();
        assertThat(totpService.verify("SECRET", "")).isFalse();
        assertThat(totpService.verify("SECRET", "   ")).isFalse();
    }
}
