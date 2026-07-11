package com.school.app.fee;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.common.notification.NotificationEventType;
import com.school.app.common.notification.NotificationService;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import com.school.app.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeeService {

    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final FeeMapper feeMapper;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public List<FeeDto> getByStudent(UUID studentId, User currentUser) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student with id " + studentId + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only view their own child's fee status");
        }

        return feeRepository.findByStudentId(studentId).stream()
                .map(feeMapper::toDto)
                .toList();
    }

    public FeeDto update(UUID id, FeeUpdateRequest request) {
        Fee fee = feeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fee with id " + id + " not found"));

        if (request.amountPaid() != null) {
            if (request.amountPaid().compareTo(fee.getAmountDue()) > 0) {
                throw new BadRequestException("Amount paid cannot exceed amount due");
            }
            fee.setAmountPaid(request.amountPaid());
        }

        fee.setStatus(request.status() != null ? request.status() : deriveStatus(fee));

        FeeDto dto = feeMapper.toDto(feeRepository.save(fee));

        // fee.getStudent() is a lazy proxy from a closed session — safe for .getId(), but a
        // fresh fetch is needed before reading .getName()/.getParent() off it.
        if (dto.status() == FeeStatus.OVERDUE) {
            studentRepository.findById(fee.getStudent().getId()).ifPresent(student -> {
                if (student.getParent() != null) {
                    userRepository.findById(student.getParent().getId()).ifPresent(parent ->
                            notificationService.notify(
                                    NotificationEventType.FEE_OVERDUE,
                                    parent,
                                    "Fee overdue for " + student.getName(),
                                    "The " + fee.getTerm() + " fee for " + student.getName() + " is overdue. Amount due: "
                                            + fee.getAmountDue().subtract(fee.getAmountPaid()) + "."));
                }
            });
        }

        return dto;
    }

    private FeeStatus deriveStatus(Fee fee) {
        BigDecimal amountPaid = fee.getAmountPaid();
        BigDecimal amountDue = fee.getAmountDue();

        if (amountPaid.compareTo(amountDue) >= 0) {
            return FeeStatus.PAID;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            return FeeStatus.PARTIAL;
        }
        if (fee.getDueDate().isBefore(LocalDate.now())) {
            return FeeStatus.OVERDUE;
        }
        return FeeStatus.PENDING;
    }
}
