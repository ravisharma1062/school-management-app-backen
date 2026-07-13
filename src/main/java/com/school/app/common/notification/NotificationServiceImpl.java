package com.school.app.common.notification;

import com.school.app.common.exception.NotConfiguredException;
import com.school.app.common.notification.email.EmailProvider;
import com.school.app.common.notification.sms.SmsProvider;
import com.school.app.platform.EntitlementService;
import com.school.app.platform.FeatureKey;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationLogRepository logRepository;
    private final SmsProvider smsProvider;
    private final EmailProvider emailProvider;
    private final EntitlementService entitlementService;

    @Override
    public void notify(NotificationEventType eventType, User recipient, String subject, String message) {
        NotificationPreference preference = preferenceRepository.findByEventType(eventType).orElse(null);
        if (preference == null) {
            log.warn("No notification preference row for event type {}; skipping", eventType);
            return;
        }

        if (preference.isSmsEnabled()) {
            sendVia(NotificationChannel.SMS, eventType, recipient, subject, message);
        }
        if (preference.isEmailEnabled()) {
            sendVia(NotificationChannel.EMAIL, eventType, recipient, subject, message);
        }
    }

    private void sendVia(
            NotificationChannel channel, NotificationEventType eventType, User recipient, String subject, String message) {
        String recipientAddress = channel == NotificationChannel.SMS ? recipient.getPhone() : recipient.getEmail();
        if (recipientAddress == null || recipientAddress.isBlank()) {
            writeLog(eventType, channel, "(missing)", subject, NotificationStatus.SKIPPED,
                    "Recipient has no " + (channel == NotificationChannel.SMS ? "phone" : "email") + " on file");
            return;
        }

        FeatureKey requiredFeature = channel == NotificationChannel.SMS
                ? FeatureKey.SMS_NOTIFICATIONS
                : FeatureKey.EMAIL_NOTIFICATIONS;
        if (!entitlementService.isEntitled(requiredFeature)) {
            log.info("{} not entitled for this school's plan; skipping {} notification to {}",
                    requiredFeature, eventType, recipientAddress);
            writeLog(eventType, channel, recipientAddress, subject, NotificationStatus.SKIPPED_NOT_ENTITLED,
                    "This school's plan does not include " + requiredFeature.name());
            return;
        }

        try {
            if (channel == NotificationChannel.SMS) {
                smsProvider.send(recipientAddress, message);
            } else {
                emailProvider.send(recipientAddress, subject, message);
            }
            writeLog(eventType, channel, recipientAddress, subject, NotificationStatus.SENT, null);
        } catch (NotConfiguredException e) {
            log.info("{} channel not configured, skipping {} notification to {}: {}",
                    channel, eventType, recipientAddress, e.getMessage());
            writeLog(eventType, channel, recipientAddress, subject, NotificationStatus.SKIPPED, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to send {} notification for {} to {}", channel, eventType, recipientAddress, e);
            writeLog(eventType, channel, recipientAddress, subject, NotificationStatus.FAILED, e.getMessage());
        }
    }

    private void writeLog(
            NotificationEventType eventType, NotificationChannel channel, String recipient,
            String subject, NotificationStatus status, String error) {
        logRepository.save(NotificationLog.builder()
                .eventType(eventType)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .status(status)
                .error(error)
                .build());
    }
}
