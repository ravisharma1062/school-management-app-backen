package com.school.app.platform;

import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationLogRepository;
import com.school.app.school.School;
import com.school.app.school.SchoolRepository;
import com.school.app.school.SchoolStatus;
import com.school.app.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformSchoolService {

    private final SchoolRepository schoolRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementRepository entitlementRepository;
    private final StudentRepository studentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<SchoolAdminDto> list(Pageable pageable) {
        return schoolRepository.findAll(pageable).map(SchoolAdminDto::from);
    }

    @Transactional(readOnly = true)
    public SchoolAdminDto get(UUID id) {
        return SchoolAdminDto.from(requireSchool(id));
    }

    /**
     * Manually drives the lifecycle {@code JwtAuthFilter} enforces (suspend/reactivate/cancel)
     * until MT-5 automates it via billing webhooks. Mirrors the new status onto the school's
     * {@code Subscription} too, since that's what the tenant-facing account screen reads.
     */
    @Transactional
    public SchoolAdminDto updateStatus(UUID id, SchoolStatus newStatus, PlatformUser actor) {
        School school = requireSchool(id);
        SchoolStatus oldStatus = school.getStatus();
        school.setStatus(newStatus);
        schoolRepository.save(school);

        subscriptionRepository.findBySchoolId(id).ifPresent(subscription -> {
            subscription.setStatus(newStatus);
            subscriptionRepository.save(subscription);
        });

        auditService.record(actor, AuditAction.SCHOOL_STATUS_CHANGED, id,
                "Changed status of '" + school.getName() + "' from " + oldStatus + " to " + newStatus);
        return SchoolAdminDto.from(school);
    }

    /**
     * MT-6c usage metering — cross-tenant reads via the bypass queries, since platform tokens
     * carry no tenant for {@code @TenantId} to filter on (see {@code SchoolTenantResolver} Javadoc).
     */
    @Transactional(readOnly = true)
    public SchoolUsageDto getUsage(UUID schoolId) {
        requireSchool(schoolId);
        long activeStudents = studentRepository.countActiveBySchoolIdBypassingTenantFilter(schoolId);
        Integer maxStudentsLimit = subscriptionRepository.findBySchoolId(schoolId)
                .flatMap(sub -> entitlementRepository.findBySubscriptionIdAndFeatureKey(sub.getId(), FeatureKey.MAX_STUDENTS))
                .map(Entitlement::getLimitValue)
                .orElse(null);

        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        long emails = notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(schoolId, "EMAIL", monthStart);
        long sms = notificationLogRepository.countSentByChannelSinceBypassingTenantFilter(schoolId, "SMS", monthStart);

        return new SchoolUsageDto(schoolId, activeStudents, maxStudentsLimit, emails, sms);
    }

    private School requireSchool(UUID id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("School " + id + " not found"));
    }
}
