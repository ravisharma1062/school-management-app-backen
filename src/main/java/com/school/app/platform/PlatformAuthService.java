package com.school.app.platform;

import com.school.app.common.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformAuthService {

    private final PlatformUserRepository platformUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TotpService totpService;

    /**
     * Does not go through Spring Security's {@code AuthenticationManager} — see
     * {@link PlatformUserLookupService} for why. Verifies password (and MFA code, if the account
     * has enrolled) directly.
     */
    public PlatformAuthResponse login(PlatformLoginRequest request) {
        PlatformUser user = platformUserRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        boolean mfaEnrolled = user.getMfaSecret() != null;
        if (mfaEnrolled && !totpService.verify(user.getMfaSecret(), request.mfaCode())) {
            throw new BadCredentialsException("Invalid or missing MFA code");
        }

        return tokenResponse(user);
    }

    public PlatformAuthResponse refresh(PlatformRefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtService.isPlatformToken(token) || !jwtService.isRefreshToken(token)) {
            throw new BadCredentialsException("Provided token is not a valid platform refresh token");
        }

        String email = jwtService.extractUsername(token);
        PlatformUser user = platformUserRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!jwtService.isTokenValid(token, user)) {
            throw new BadCredentialsException("Refresh token is invalid or expired");
        }

        return tokenResponse(user);
    }

    /** Step 1 of enrolment: generate a candidate secret. Not persisted until {@link #confirmMfa} verifies it. */
    public MfaEnrollResponse enrollMfa(PlatformUser currentUser) {
        String secret = totpService.generateSecret();
        return new MfaEnrollResponse(secret, totpService.otpAuthUri(secret, currentUser.getEmail()));
    }

    /** Step 2: only once the admin proves they captured the secret correctly does it become active. */
    public void confirmMfa(PlatformUser currentUser, MfaConfirmRequest request) {
        if (!totpService.verify(request.secret(), request.code())) {
            throw new BadCredentialsException("Invalid MFA code");
        }
        PlatformUser managed = platformUserRepository.findById(currentUser.getId()).orElseThrow();
        managed.setMfaSecret(request.secret());
        platformUserRepository.save(managed);
    }

    private PlatformAuthResponse tokenResponse(PlatformUser user) {
        String accessToken = jwtService.generatePlatformAccessToken(user.getEmail(), user.getPlatformRole());
        String refreshToken = jwtService.generatePlatformRefreshToken(user.getEmail(), user.getPlatformRole());
        return new PlatformAuthResponse(accessToken, refreshToken, user.getPlatformRole(), user.getMfaSecret() != null);
    }
}
