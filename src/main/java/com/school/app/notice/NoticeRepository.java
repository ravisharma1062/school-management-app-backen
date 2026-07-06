package com.school.app.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface NoticeRepository extends JpaRepository<Notice, UUID> {

    Page<Notice> findByTargetRoleIn(Collection<TargetRole> targetRoles, Pageable pageable);

    Page<Notice> findByTargetRole(TargetRole targetRole, Pageable pageable);
}
