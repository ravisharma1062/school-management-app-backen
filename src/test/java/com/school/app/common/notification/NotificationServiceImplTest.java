package com.school.app.common.notification;

import com.school.app.common.exception.NotConfiguredException;
import com.school.app.common.notification.email.EmailProvider;
import com.school.app.common.notification.sms.SmsProvider;
import com.school.app.user.Role;
import com.school.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    private NotificationPreferenceRepository preferenceRepository;
    private NotificationLogRepository logRepository;
    private SmsProvider smsProvider;
    private EmailProvider emailProvider;
    private NotificationServiceImpl service;

    private User recipient;

    @BeforeEach
    void setUp() {
        preferenceRepository = mock(NotificationPreferenceRepository.class);
        logRepository = mock(NotificationLogRepository.class);
        smsProvider = mock(SmsProvider.class);
        emailProvider = mock(EmailProvider.class);
        service = new NotificationServiceImpl(preferenceRepository, logRepository, smsProvider, emailProvider);

        recipient = User.builder()
                .id(UUID.randomUUID())
                .name("Parent")
                .email("parent@school.app")
                .phone("+911234567890")
                .role(Role.PARENT)
                .build();
    }

    @Test
    void doesNothingWhenNoPreferenceRowExists() {
        when(preferenceRepository.findByEventType(NotificationEventType.ATTENDANCE_ABSENT)).thenReturn(Optional.empty());

        service.notify(NotificationEventType.ATTENDANCE_ABSENT, recipient, "subj", "msg");

        verifyNoInteractions(smsProvider, emailProvider, logRepository);
    }

    @Test
    void sendsOnlyEnabledChannelsAndLogsSuccess() {
        NotificationPreference pref = NotificationPreference.builder()
                .eventType(NotificationEventType.ATTENDANCE_ABSENT)
                .smsEnabled(true)
                .emailEnabled(false)
                .build();
        when(preferenceRepository.findByEventType(NotificationEventType.ATTENDANCE_ABSENT)).thenReturn(Optional.of(pref));

        service.notify(NotificationEventType.ATTENDANCE_ABSENT, recipient, "subj", "msg");

        verify(smsProvider).send(eq(recipient.getPhone()), eq("msg"));
        verifyNoInteractions(emailProvider);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
    }

    @Test
    void logsSkippedWhenProviderNotConfigured() {
        NotificationPreference pref = NotificationPreference.builder()
                .eventType(NotificationEventType.USER_WELCOME)
                .smsEnabled(false)
                .emailEnabled(true)
                .build();
        when(preferenceRepository.findByEventType(NotificationEventType.USER_WELCOME)).thenReturn(Optional.of(pref));
        doThrow(new NotConfiguredException("no creds")).when(emailProvider).send(any(), any(), any());

        service.notify(NotificationEventType.USER_WELCOME, recipient, "subj", "msg");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }

    @Test
    void logsFailedAndDoesNotThrowWhenProviderErrors() {
        NotificationPreference pref = NotificationPreference.builder()
                .eventType(NotificationEventType.FEE_OVERDUE)
                .smsEnabled(true)
                .emailEnabled(false)
                .build();
        when(preferenceRepository.findByEventType(NotificationEventType.FEE_OVERDUE)).thenReturn(Optional.of(pref));
        doThrow(new RuntimeException("network down")).when(smsProvider).send(any(), any());

        service.notify(NotificationEventType.FEE_OVERDUE, recipient, "subj", "msg");

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void skipsAndLogsWhenRecipientHasNoPhoneForSms() {
        User noPhone = User.builder()
                .id(UUID.randomUUID())
                .name("No Phone")
                .email("nophone@school.app")
                .role(Role.PARENT)
                .build();
        NotificationPreference pref = NotificationPreference.builder()
                .eventType(NotificationEventType.ATTENDANCE_ABSENT)
                .smsEnabled(true)
                .emailEnabled(false)
                .build();
        when(preferenceRepository.findByEventType(NotificationEventType.ATTENDANCE_ABSENT)).thenReturn(Optional.of(pref));

        service.notify(NotificationEventType.ATTENDANCE_ABSENT, noPhone, "subj", "msg");

        verifyNoInteractions(smsProvider);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SKIPPED);
    }
}
