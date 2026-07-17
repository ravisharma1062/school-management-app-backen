package com.school.app.platform;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.DuplicateResourceException;
import com.school.app.common.exception.TooManyRequestsException;
import com.school.app.common.notification.email.EmailProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicSignupServiceTest {

    @Mock
    private SignupRequestRepository signupRequestRepository;
    @Mock
    private PlatformSettingsRepository platformSettingsRepository;
    @Mock
    private ProvisioningService provisioningService;
    @Mock
    private CaptchaVerifier captchaVerifier;
    @Mock
    private PublicSignupRateLimiter rateLimiter;
    @Mock
    private EmailProvider emailProvider;

    @InjectMocks
    private PublicSignupService publicSignupService;

    private PublicSignupRequest request;

    @BeforeEach
    void setUp() {
        request = new PublicSignupRequest(
                "  Springfield High  ", "  Jane Doe  ", "  JANE@SCHOOL.APP  ", "9999999999",
                PlanCode.STANDARD, true, false, "captcha-token");
        ReflectionTestUtils.setField(publicSignupService, "platformTeamEmail", "platform-team@school.app");
    }

    @Test
    void rejectsWhenTheRateLimitIsExhausted() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(false);

        assertThatThrownBy(() -> publicSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(captchaVerifier, never()).verify(any(), any());
        verify(signupRequestRepository, never()).save(any());
    }

    @Test
    void rejectsAFailedCaptcha() {
        when(rateLimiter.tryConsume("1.2.3.4")).thenReturn(true);
        when(captchaVerifier.verify("captcha-token", "1.2.3.4")).thenReturn(false);

        assertThatThrownBy(() -> publicSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(BadRequestException.class);
        verify(signupRequestRepository, never()).save(any());
    }

    @Test
    void rejectsADuplicatePendingEmail() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(signupRequestRepository.findByContactEmailAndStatus("jane@school.app", SignupRequestStatus.NEW))
                .thenReturn(Optional.of(SignupRequest.builder().id(UUID.randomUUID()).build()));

        assertThatThrownBy(() -> publicSignupService.submit(request, "1.2.3.4"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(signupRequestRepository, never()).save(any());
    }

    @Test
    void savesATrimmedNormalizedSignupRequestAndNotifiesWithoutAutoApproving() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(signupRequestRepository.findByContactEmailAndStatus(eq("jane@school.app"), eq(SignupRequestStatus.NEW)))
                .thenReturn(Optional.empty());
        when(signupRequestRepository.save(any(SignupRequest.class))).thenAnswer(inv -> {
            SignupRequest sr = inv.getArgument(0);
            sr.setId(UUID.randomUUID());
            return sr;
        });
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        publicSignupService.submit(request, "1.2.3.4");

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(signupRequestRepository).save(captor.capture());
        SignupRequest saved = captor.getValue();
        assertThatCode(() -> {
            assert saved.getSchoolName().equals("Springfield High");
            assert saved.getContactName().equals("Jane Doe");
            assert saved.getContactEmail().equals("jane@school.app");
        }).doesNotThrowAnyException();
        verify(provisioningService, never()).approve(any(), any(), any());
        verify(emailProvider).send(anyString(), anyString(), anyString());
    }

    @Test
    void blankContactPhoneIsNormalizedToNull() {
        PublicSignupRequest blankPhone = new PublicSignupRequest(
                "Springfield High", "Jane Doe", "jane@school.app", "   ",
                PlanCode.BASIC, false, false, "captcha-token");
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(signupRequestRepository.findByContactEmailAndStatus(anyString(), eq(SignupRequestStatus.NEW)))
                .thenReturn(Optional.empty());
        when(signupRequestRepository.save(any(SignupRequest.class))).thenAnswer(inv -> {
            SignupRequest sr = inv.getArgument(0);
            sr.setId(UUID.randomUUID());
            return sr;
        });
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        publicSignupService.submit(blankPhone, "1.2.3.4");

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(signupRequestRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getContactPhone()).isNull();
    }

    @Test
    void autoApprovesWhenPlatformSettingsOptIn() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(signupRequestRepository.findByContactEmailAndStatus(anyString(), eq(SignupRequestStatus.NEW)))
                .thenReturn(Optional.empty());
        UUID savedId = UUID.randomUUID();
        when(signupRequestRepository.save(any(SignupRequest.class))).thenAnswer(inv -> {
            SignupRequest sr = inv.getArgument(0);
            sr.setId(savedId);
            return sr;
        });
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.of(
                PlatformSettings.builder().id(PlatformSettings.SINGLETON_ID).autoApproveSignups(true).build()));
        when(provisioningService.approve(eq(savedId), any(ProvisionApproveRequest.class), isNull()))
                .thenReturn(new ProvisioningService.ProvisionOutcome(
                        new ProvisionResultDto(UUID.randomUUID(), "springfield-high", "jane@school.app"), "raw-token"));

        publicSignupService.submit(request, "1.2.3.4");

        verify(provisioningService).approve(eq(savedId), any(ProvisionApproveRequest.class), isNull());
        verify(emailProvider).send(anyString(), anyString(), anyString());
    }

    @Test
    void notificationFailureDoesNotPropagateOutOfSubmit() {
        when(rateLimiter.tryConsume(anyString())).thenReturn(true);
        when(captchaVerifier.verify(anyString(), anyString())).thenReturn(true);
        when(signupRequestRepository.findByContactEmailAndStatus(anyString(), eq(SignupRequestStatus.NEW)))
                .thenReturn(Optional.empty());
        when(signupRequestRepository.save(any(SignupRequest.class))).thenAnswer(inv -> {
            SignupRequest sr = inv.getArgument(0);
            sr.setId(UUID.randomUUID());
            return sr;
        });
        when(platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)).thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new RuntimeException("smtp down")).when(emailProvider)
                .send(anyString(), anyString(), anyString());

        assertThatCode(() -> publicSignupService.submit(request, "1.2.3.4")).doesNotThrowAnyException();
    }
}
