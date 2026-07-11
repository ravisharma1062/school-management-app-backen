package com.school.app.leaverequest;

import com.school.app.user.User;
import org.springframework.stereotype.Component;

@Component
public class LeaveRequestMapper {

    public LeaveRequestDto toDto(LeaveRequest leaveRequest) {
        User reviewer = leaveRequest.getReviewedBy();
        return new LeaveRequestDto(
                leaveRequest.getId(),
                leaveRequest.getRequester().getId(),
                leaveRequest.getType(),
                leaveRequest.getFromDate(),
                leaveRequest.getToDate(),
                leaveRequest.getReason(),
                leaveRequest.getStatus(),
                reviewer != null ? reviewer.getId() : null,
                leaveRequest.getCreatedAt()
        );
    }
}
