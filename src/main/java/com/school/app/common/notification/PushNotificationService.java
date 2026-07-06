package com.school.app.common.notification;

import com.school.app.homework.HomeworkDto;
import com.school.app.notice.NoticeDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Publishes push notification events for new notices and homework.
 * Currently logs the event; swap the body of these methods for a real
 * Firebase Cloud Messaging call once FCM project credentials are provisioned.
 */
@Slf4j
@Service
public class PushNotificationService {

    public void publishHomeworkCreated(HomeworkDto homework) {
        log.info("Publishing FCM push event: new homework '{}' for class {}-{}",
                homework.title(), homework.studentClass(), homework.section());
    }

    public void publishNoticeCreated(NoticeDto notice) {
        log.info("Publishing FCM push event: new notice '{}' targeted at {}",
                notice.title(), notice.targetRole());
    }
}
