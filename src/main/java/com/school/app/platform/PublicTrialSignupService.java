package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.TooManyRequestsException;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * MT-6b: the public, no-review counterpart of {@link PublicSignupService} — same rate-limit +
 * CAPTCHA + input-hygiene posture (see its Javadoc), but provisions a real {@code TRIAL} school
 * immediately via {@link ProvisioningService} instead of queueing a {@link SignupRequest} for an
 * operator to approve.
 */
@Service
@RequiredArgsConstructor
public class PublicTrialSignupService {

    private final TrialSignupRateLimiter rateLimiter;
    private final CaptchaVerifier captchaVerifier;
    private final UserRepository userRepository;
    private final ProvisioningService provisioningService;

    public ProvisionResultDto submit(PublicTrialSignupRequest request, String clientIp) {
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new TooManyRequestsException("Too many trial signups from this address. Please try again later.");
        }
        if (!captchaVerifier.verify(request.captchaToken(), clientIp)) {
            throw new BadRequestException("CAPTCHA verification failed. Please try again.");
        }

        String normalizedEmail = request.contactEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailBypassingTenantFilter(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        return provisioningService.provisionSelfServiceTrial(request).result();
    }
}
