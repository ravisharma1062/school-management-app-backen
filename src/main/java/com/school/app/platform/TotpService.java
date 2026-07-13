package com.school.app.platform;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * TOTP (RFC 6238) enrolment + verification for platform-admin MFA. No QR image is generated
 * server-side (would pull in a zxing dependency for one PNG) — the operator console renders a QR
 * from {@link #otpAuthUri} client-side, or the admin can type the secret into their authenticator
 * app directly.
 */
@Service
public class TotpService {

    private static final String ISSUER = "SchoolApp Operator";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String otpAuthUri(String secret, String accountEmail) {
        String label = URLEncoder.encode(ISSUER + ":" + accountEmail, StandardCharsets.UTF_8);
        String issuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuer;
    }

    public boolean verify(String secret, String code) {
        return secret != null && code != null && !code.isBlank() && codeVerifier.isValidCode(secret, code);
    }
}
