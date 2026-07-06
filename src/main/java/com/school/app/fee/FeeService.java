package com.school.app.fee;

import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
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

        return feeMapper.toDto(feeRepository.save(fee));
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
