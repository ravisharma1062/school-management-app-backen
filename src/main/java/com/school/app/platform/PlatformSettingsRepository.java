package com.school.app.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, UUID> {
}
