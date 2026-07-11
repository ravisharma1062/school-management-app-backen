package com.school.app.common.notification;

import com.school.app.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationPreferenceMapper preferenceMapper;

    public List<NotificationPreferenceDto> list() {
        return preferenceRepository.findAll().stream()
                .map(preferenceMapper::toDto)
                .toList();
    }

    public NotificationPreferenceDto update(NotificationEventType eventType, NotificationPreferenceUpdateRequest request) {
        NotificationPreference preference = preferenceRepository.findById(eventType)
                .orElseThrow(() -> new ResourceNotFoundException("No preference row for event type " + eventType));

        preference.setSmsEnabled(request.smsEnabled());
        preference.setEmailEnabled(request.emailEnabled());

        return preferenceMapper.toDto(preferenceRepository.save(preference));
    }
}
