package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.NotConfiguredException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.email.EmailProvider;
import com.school.app.common.security.TenantContext;
import com.school.app.common.security.TenantRlsTransactionListener;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.user.LanguageCode;
import com.school.app.user.Role;
import com.school.app.user.UserRepository;
import com.school.app.user.UserStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Creates a fully-usable, fully-isolated tenant in one atomic transaction: {@code School} +
 * {@code Subscription} + its plan-default {@code Entitlement}s + a {@code PENDING_ACTIVATION}
 * admin {@code User} + a single-use invite link. Never generates, stores, or emails a plaintext
 * password (Architecture Decision #4) — the admin sets their own via {@link ActivationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvisioningService {

    private static final int TRIAL_DAYS = 14;
    private static final int BILLING_PERIOD_DAYS = 30;
    private static final int ACTIVATION_TOKEN_VALID_DAYS = 7;

    private final SignupRequestRepository signupRequestRepository;
    private final SchoolRepository schoolRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementRepository entitlementRepository;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final AuditService auditService;
    private final EmailProvider emailProvider;
    private final PasswordEncoder passwordEncoder;
    private final TenantRlsTransactionListener tenantRlsTransactionListener;
    private final EntityManager entityManager;

    @Value("${app.operator.activation-base-url:http://localhost:5174/activate}")
    private String activationBaseUrl;

    /**
     * Carries the raw (unhashed) activation token alongside the public result — {@code result}
     * alone is what {@link PlatformSignupRequestService} exposes over HTTP; the raw token exists
     * only long enough to email it (or, in tests, to drive the activation flow end-to-end without
     * a real inbox).
     */
    public record ProvisionOutcome(ProvisionResultDto result, String rawActivationToken) {
    }

    @Transactional
    public ProvisionOutcome approve(UUID signupRequestId, ProvisionApproveRequest request, PlatformUser actor) {
        SignupRequest signupRequest = signupRequestRepository.findById(signupRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Signup request " + signupRequestId + " not found"));
        if (signupRequest.getStatus() != SignupRequestStatus.NEW) {
            throw new BadRequestException("This signup request has already been " + signupRequest.getStatus().name().toLowerCase(Locale.ROOT));
        }

        PlanCode planCode = request.planCode() != null ? request.planCode() : signupRequest.getDesiredPlan();
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan " + planCode + " not found"));

        School school = schoolRepository.save(School.builder()
                .name(signupRequest.getSchoolName())
                .slug(uniqueSlug(signupRequest.getSchoolName()))
                .status(request.startAsTrial() ? SchoolStatus.TRIAL : SchoolStatus.ACTIVE)
                .build());

        Instant now = Instant.now();
        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .school(school)
                .plan(plan)
                .status(school.getStatus())
                .currentPeriodStart(request.startAsTrial() ? null : now)
                .currentPeriodEnd(request.startAsTrial() ? null : now.plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS))
                .trialEndsAt(request.startAsTrial() ? now.plus(TRIAL_DAYS, ChronoUnit.DAYS) : null)
                .build());

        var defaults = PlanDefaults.forPlan(plan.getCode());
        for (FeatureKey key : FeatureKey.values()) {
            var planDefault = defaults.get(key);
            // The two channel toggles the business explicitly sells are honoured from what the
            // school actually asked for at signup, overriding the plan's blanket default for them.
            boolean enabled = switch (key) {
                case EMAIL_NOTIFICATIONS -> signupRequest.isWantsEmail();
                case SMS_NOTIFICATIONS -> signupRequest.isWantsSms();
                default -> planDefault.enabled();
            };
            entitlementRepository.save(Entitlement.builder()
                    .subscription(subscription)
                    .featureKey(key)
                    .enabled(enabled)
                    .limitValue(planDefault.limitValue())
                    .build());
        }

        // Everything above this line is on non-@TenantId (global) tables. The admin User is
        // tenant-scoped, but this transaction's Hibernate Session had its tenant identifier
        // resolved (to whatever TenantContext held, or nothing) before this method even started —
        // see SchoolTenantResolver's Javadoc. TenantContext.set() below fixes RLS (re-applied
        // on-demand) but NOT Hibernate's own @TenantId population, so the insert itself goes
        // through a native query that bypasses that resolver entirely, supplying school_id itself.
        TenantContext.set(school.getId());
        tenantRlsTransactionListener.applyCurrentTenant(entityManager);

        UUID adminId = UUID.randomUUID();
        // Unusable, never-revealed placeholder password — this account cannot authenticate until
        // ActivationService sets a real one. Belt-and-suspenders alongside the PENDING_ACTIVATION
        // status, which User#isEnabled() also checks.
        String placeholderPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        userRepository.insertBypassingTenantFilter(
                adminId, school.getId(), signupRequest.getContactName(), signupRequest.getContactEmail(),
                placeholderPasswordHash, Role.ADMIN.name(), LanguageCode.EN.name(), UserStatus.PENDING_ACTIVATION.name());

        String rawToken = ActivationTokens.generateRaw();
        activationTokenRepository.save(ActivationToken.builder()
                .schoolId(school.getId())
                .userId(adminId)
                .tokenHash(ActivationTokens.hash(rawToken))
                .expiresAt(Instant.now().plus(ACTIVATION_TOKEN_VALID_DAYS, ChronoUnit.DAYS))
                .build());

        signupRequest.setStatus(SignupRequestStatus.APPROVED);
        signupRequestRepository.save(signupRequest);

        String adminEmail = signupRequest.getContactEmail();
        auditService.record(actor, AuditAction.SIGNUP_REQUEST_APPROVED, school.getId(),
                "Provisioned '" + school.getName() + "' (" + plan.getCode() + ", "
                        + (request.startAsTrial() ? "trial" : "active") + ") — invite sent to " + adminEmail);

        sendActivationEmail(adminEmail, school.getName(), rawToken);

        return new ProvisionOutcome(new ProvisionResultDto(school.getId(), school.getSlug(), adminEmail), rawToken);
    }

    @Transactional
    public void reject(UUID signupRequestId, PlatformUser actor) {
        SignupRequest signupRequest = signupRequestRepository.findById(signupRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Signup request " + signupRequestId + " not found"));
        if (signupRequest.getStatus() != SignupRequestStatus.NEW) {
            throw new BadRequestException("This signup request has already been " + signupRequest.getStatus().name().toLowerCase(Locale.ROOT));
        }
        signupRequest.setStatus(SignupRequestStatus.REJECTED);
        signupRequestRepository.save(signupRequest);
        auditService.record(actor, AuditAction.SIGNUP_REQUEST_REJECTED, null,
                "Rejected signup request from " + signupRequest.getContactEmail() + " (" + signupRequest.getSchoolName() + ")");
    }

    private void sendActivationEmail(String toEmail, String schoolName, String rawToken) {
        String activationLink = activationBaseUrl + "?token=" + rawToken;
        try {
            emailProvider.send(toEmail, "Activate your School App admin account for " + schoolName,
                    "Welcome to School App! Set your password to activate your admin account:\n\n"
                            + activationLink + "\n\nThis link expires in " + ACTIVATION_TOKEN_VALID_DAYS + " days.");
        } catch (NotConfiguredException e) {
            log.info("Email provider not configured; activation link for {} is {}", toEmail, activationLink);
        } catch (Exception e) {
            log.error("Failed to send activation email to {}", toEmail, e);
        }
    }

    private String uniqueSlug(String schoolName) {
        String base = schoolName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "school";
        }
        String slug = base;
        int suffix = 1;
        while (schoolRepository.findBySlug(slug).isPresent()) {
            slug = base + "-" + (++suffix);
        }
        return slug;
    }
}
