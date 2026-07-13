package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.security.TenantContext;
import com.school.app.common.security.TenantRlsTransactionListener;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import com.school.app.user.UserStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public (no-JWT) account-activation flow — the invite-link counterpart to {@link ProvisioningService}. */
@Service
@RequiredArgsConstructor
public class ActivationService {

    private final ActivationTokenRepository activationTokenRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRlsTransactionListener tenantRlsTransactionListener;
    private final EntityManager entityManager;

    /**
     * Reads via {@link UserRepository#findByIdBypassingTenantFilter} rather than setting
     * {@link TenantContext} — a read-only display of the invited email needs no tenant scoping,
     * and the token itself (not tenant membership) is what authorizes seeing it.
     */
    @Transactional(readOnly = true)
    public ActivationInfoDto getInfo(String rawToken) {
        ActivationToken activationToken = requireValidToken(rawToken);
        User user = userRepository.findByIdBypassingTenantFilter(activationToken.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Activation link no longer valid"));
        School school = schoolRepository.findById(activationToken.getSchoolId())
                .orElseThrow(() -> new ResourceNotFoundException("Activation link no longer valid"));
        return new ActivationInfoDto(school.getName(), user.getEmail());
    }

    @Transactional
    public void activate(ActivateAccountRequest request) {
        ActivationToken activationToken = requireValidToken(request.token());

        // This transaction began with no tenant known (public endpoint) — set it now that the
        // token has told us, and re-apply the RLS session variable directly for the remainder of
        // the transaction (TenantRlsTransactionListener's begin-of-transaction hook already ran).
        // That fixes RLS but NOT Hibernate's own @TenantId resolution (fixed at Session-creation,
        // before this method ran — see SchoolTenantResolver's Javadoc), so the write itself goes
        // through a native query that bypasses that resolver entirely, mirroring ProvisioningService.
        TenantContext.set(activationToken.getSchoolId());
        tenantRlsTransactionListener.applyCurrentTenant(entityManager);

        int updated = userRepository.activateBypassingTenantFilter(
                activationToken.getUserId(), passwordEncoder.encode(request.newPassword()), UserStatus.ACTIVE.name());
        if (updated == 0) {
            throw new ResourceNotFoundException("Activation link no longer valid");
        }

        activationToken.setUsed(true);
        activationTokenRepository.save(activationToken);
    }

    private ActivationToken requireValidToken(String rawToken) {
        ActivationToken token = activationTokenRepository.findByTokenHash(ActivationTokens.hash(rawToken))
                .orElseThrow(() -> new BadRequestException("Invalid or expired activation link"));
        if (token.isUsed() || token.isExpired()) {
            throw new BadRequestException("Invalid or expired activation link");
        }
        return token;
    }
}
