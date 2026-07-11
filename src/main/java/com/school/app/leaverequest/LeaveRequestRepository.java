package com.school.app.leaverequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByRequesterId(UUID requesterId, Pageable pageable);

    Page<LeaveRequest> findByRequesterIdAndStatus(UUID requesterId, LeaveStatus status, Pageable pageable);
}
