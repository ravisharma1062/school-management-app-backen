package com.school.app.leaverequest;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveRequestMapper leaveRequestMapper;

    public LeaveRequestDto create(LeaveRequestCreateRequest request, User currentUser) {
        if (request.toDate().isBefore(request.fromDate())) {
            throw new BadRequestException("toDate must not be before fromDate");
        }

        LeaveRequest leaveRequest = LeaveRequest.builder()
                .requester(currentUser)
                .type(request.type())
                .fromDate(request.fromDate())
                .toDate(request.toDate())
                .reason(request.reason())
                .build();

        return leaveRequestMapper.toDto(leaveRequestRepository.save(leaveRequest));
    }

    /** Admins see every request (optionally filtered by status); everyone else sees only their own. */
    public Page<LeaveRequestDto> list(LeaveStatus statusFilter, User currentUser, Pageable pageable) {
        Page<LeaveRequest> page;
        if (currentUser.getRole() == Role.ADMIN) {
            page = statusFilter != null
                    ? leaveRequestRepository.findByStatus(statusFilter, pageable)
                    : leaveRequestRepository.findAll(pageable);
        } else {
            page = statusFilter != null
                    ? leaveRequestRepository.findByRequesterIdAndStatus(currentUser.getId(), statusFilter, pageable)
                    : leaveRequestRepository.findByRequesterId(currentUser.getId(), pageable);
        }
        return page.map(leaveRequestMapper::toDto);
    }

    public LeaveRequestDto review(UUID id, LeaveRequestReviewRequest request, User currentUser) {
        if (request.status() == LeaveStatus.PENDING) {
            throw new BadRequestException("status must be APPROVED or REJECTED");
        }

        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request with id " + id + " not found"));

        leaveRequest.setStatus(request.status());
        leaveRequest.setReviewedBy(currentUser);

        return leaveRequestMapper.toDto(leaveRequestRepository.save(leaveRequest));
    }
}
