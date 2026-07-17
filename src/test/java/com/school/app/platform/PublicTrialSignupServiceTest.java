package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.TooManyRequestsException;
import com.school.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicTrialSignupServiceTest {

    @Mock
    private TrialSignupRateLimiter rateLimiter;
    @Mock
    private CaptchaVerifier captchaVerifier;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProvisioningService provisioningService;

    @InjectMocks
    private PublicTrialSignupService publicTrialSignupService;

    private PublicTrialSignupRequest request;

    @BeforeEach
    void setUp() {
        request = new PublicTrialSignupRequest(
                "Springfield High", "Jane Doe", "  JANE@SCHOOL.APP  ", null, true, false, "captcha-token");
    }

    @Test
    void rejectsWhenTheRateLimitIsExhausted() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(false);

        assertThatThrownBy(() -> publicTrialSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(captchaVerifier, never()).verify(any(), any());
    }

    @Test
    void rejectsAFailedCaptcha() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(true);
        when(captchaVerifier.verify("captcha-token", "1.2.3.4")).thenReturn(false);

        assertThatThrownBy(() -> publicTrialSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).existsByEmailBypassingTenantFilter(any());
    }

    @Test
    void rejectsAnEmailThatAlreadyExistsAcrossAnyTenant() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByEmailBypassingTenantFilter("jane@school.app")).thenReturn(true);

        assertThatThrownBy(() -> publicTrialSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(provisioningService, never()).provisionSelfServiceTrial(any());
    }

    @Test
    void provisionsATrialSchoolForANewNormalizedEmail() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(userRepository.existsByEmailBypassingTenantFilter("jane@school.app")).thenReturn(false);
        ProvisionResultDto resultDto = new ProvisionResultDto(UUID.randomUUID(), "springfield-high", "jane@school.app");
        when(provisioningService.provisionSelfServiceTrial(any(PublicTrialSignupRequest.class)))
                .thenReturn(new ProvisioningService.ProvisionOutcome(resultDto, "raw-token"));

        ProvisionResultDto result = publicTrialSignupService.submit(request, "1.2.3.4");

        assertThat(result).isEqualTo(resultDto);
        ArgumentCaptor<PublicTrialSignupRequest> captor = ArgumentCaptor.forClass(PublicTrialSignupRequest.class);
        verify(provisioningService).provisionSelfServiceTrial(captor.capture());
        assertThat(captor.getValue()).isEqualTo(request);
    }
}
