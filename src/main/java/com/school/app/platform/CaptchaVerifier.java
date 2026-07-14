package com.school.app.platform;

public interface CaptchaVerifier {

    /**
     * @return {@code true} if the token is valid, OR if CAPTCHA verification is unconfigured
     *         (deliberately permissive, unlike {@code EmailProvider}/{@code SmsProvider}/
     *         {@code PaymentGatewayProvider}'s {@code NotConfiguredException} pattern — a signup
     *         form must still be usable in dev/test before real keys exist). Implementations log
     *         loudly when running unconfigured so it isn't accidentally left that way in production.
     */
    boolean verify(String token, String remoteIp);
}
