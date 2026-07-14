package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.NotConfiguredException;
import com.school.app.common.exception.TooManyRequestsException;
import com.school.app.common.notification.email.EmailProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * The whole new attack surface MT-4 adds — see the security checklist in the plan: rate-limited,
 * CAPTCHA-verified, input-sanitised (size-capped + Bean Validation), no PII ever placed in a log
 * message or reflected back to the caller beyond what they themselves submitted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicSignupService {

    private final SignupRequestRepository signupRequestRepository;
    private final CaptchaVerifier captchaVerifier;
    private final PublicSignupRateLimiter rateLimiter;
    private final EmailProvider emailProvider;

    @Value("${app.platform.team-email:platform-team@school.app}")
    private String platformTeamEmail;

    @Transactional
    public void submit(PublicSignupRequest request, String clientIp) {
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new TooManyRequestsException("Too many signup requests from this address. Please try again later.");
        }
        if (!captchaVerifier.verify(request.captchaToken(), clientIp)) {
            throw new BadRequestException("CAPTCHA verification failed. Please try again.");
        }

        String normalizedEmail = request.contactEmail().trim().toLowerCase(Locale.ROOT);
        if (signupRequestRepository.findByContactEmailAndStatus(normalizedEmail, SignupRequestStatus.NEW).isPresent()) {
            throw new DuplicateResourceException("A signup request for this email is already pending review.");
        }

        SignupRequest saved = signupRequestRepository.save(SignupRequest.builder()
                .schoolName(request.schoolName().trim())
                .contactName(request.contactName().trim())
                .contactEmail(normalizedEmail)
                .contactPhone(request.contactPhone() != null && !request.contactPhone().isBlank() ? request.contactPhone().trim() : null)
                .desiredPlan(request.desiredPlan())
                .wantsEmail(request.wantsEmail())
                .wantsSms(request.wantsSms())
                .build());

        notifyPlatformTeam(saved);
    }

    private void notifyPlatformTeam(SignupRequest signupRequest) {
        try {
            emailProvider.send(platformTeamEmail, "New school signup request: " + signupRequest.getSchoolName(),
                    "A new signup request needs review:\n\n"
                            + "School: " + signupRequest.getSchoolName() + "\n"
                            + "Contact: " + signupRequest.getContactName() + " <" + signupRequest.getContactEmail() + ">\n"
                            + "Desired plan: " + signupRequest.getDesiredPlan() + "\n\n"
                            + "Review it in the operator console.");
        } catch (NotConfiguredException e) {
            log.info("Email provider not configured; skipping new-signup notification to platform team");
        } catch (Exception e) {
            log.error("Failed to notify platform team of new signup request", e);
        }
    }
}
