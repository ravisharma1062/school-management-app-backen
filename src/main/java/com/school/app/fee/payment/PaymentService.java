package com.school.app.fee.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.app.common.exception.BadRequestException;
import com.school.app.common.exception.ResourceNotFoundException;
import com.school.app.fee.Fee;
import com.school.app.fee.FeeRepository;
import com.school.app.fee.FeeStatus;
import com.school.app.student.Student;
import com.school.app.student.StudentRepository;
import com.school.app.user.Role;
import com.school.app.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final FeeRepository feeRepository;
    private final StudentRepository studentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayProvider gatewayProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentInitiateResponse initiate(PaymentInitiateRequest request, User currentUser) {
        Fee fee = feeRepository.findById(request.feeId())
                .orElseThrow(() -> new ResourceNotFoundException("Fee with id " + request.feeId() + " not found"));

        // fee.getStudent() is a lazy proxy from a closed session — re-fetch fully before
        // reading anything beyond its id.
        Student student = studentRepository.findById(fee.getStudent().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Student for fee " + request.feeId() + " not found"));

        if (currentUser.getRole() == Role.PARENT
                && (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId()))) {
            throw new AccessDeniedException("Parents may only pay their own child's fees");
        }

        BigDecimal amountOutstanding = fee.getAmountDue().subtract(fee.getAmountPaid());
        if (amountOutstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("This fee has nothing outstanding to pay");
        }

        PaymentGatewayProvider.GatewayOrder order =
                gatewayProvider.createOrder(amountOutstanding, "INR", "fee-" + fee.getId());

        Payment payment = Payment.builder()
                .fee(fee)
                .amount(amountOutstanding)
                .gatewayOrderId(order.orderId())
                .status(PaymentStatus.PENDING)
                .initiatedBy(currentUser)
                .build();
        paymentRepository.save(payment);

        return new PaymentInitiateResponse(order.orderId(), order.amountInSmallestUnit(), order.currency(), order.gatewayKeyId());
    }

    /**
     * Applies a gateway webhook. Wrapped in a single transaction since it must update both the
     * Payment and the Fee it belongs to atomically — the one genuinely multi-row-atomic write in
     * this codebase, unlike everywhere else where each repository call gets its own transaction.
     */
    @Transactional
    public void handleWebhook(String rawPayload, String signature) {
        if (!gatewayProvider.verifyWebhookSignature(rawPayload, signature)) {
            throw new BadRequestException("Invalid webhook signature");
        }

        JsonNode root = parsePayload(rawPayload);
        String event = root.path("event").asText("");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        String orderId = paymentEntity.path("order_id").asText(null);
        String gatewayPaymentId = paymentEntity.path("id").asText(null);

        if (orderId == null) {
            log.warn("Razorpay webhook event '{}' had no order_id in payload; ignoring", event);
            return;
        }

        Payment payment = paymentRepository.findByGatewayOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("Razorpay webhook referenced unknown order_id '{}'; ignoring", orderId);
            return;
        }

        if (event.equals("payment.captured")) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setGatewayPaymentId(gatewayPaymentId);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);
            applyToFee(payment);
        } else if (event.equals("payment.failed")) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setGatewayPaymentId(gatewayPaymentId);
            paymentRepository.save(payment);
        } else {
            log.info("Ignoring unhandled Razorpay webhook event type '{}'", event);
        }
    }

    private void applyToFee(Payment payment) {
        Fee fee = payment.getFee(); // still within the @Transactional session — safe to traverse
        fee.setAmountPaid(fee.getAmountPaid().add(payment.getAmount()));
        fee.setStatus(fee.getAmountPaid().compareTo(fee.getAmountDue()) >= 0 ? FeeStatus.PAID : FeeStatus.PARTIAL);
        feeRepository.save(fee);
    }

    private JsonNode parsePayload(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new BadRequestException("Malformed webhook payload: " + e.getMessage());
        }
    }

    public PaymentDto getById(UUID id, User currentUser) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment with id " + id + " not found"));

        if (currentUser.getRole() == Role.PARENT) {
            // payment.getFee() is a lazy proxy from a closed session — .getId() is safe, but
            // reaching further (fee.getStudent()) needs a fresh, fully-initialized Fee first.
            Fee fee = feeRepository.findById(payment.getFee().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fee for this payment not found"));
            Student student = studentRepository.findById(fee.getStudent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student for this payment not found"));
            if (student.getParent() == null || !student.getParent().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Parents may only view their own child's payments");
            }
        }

        return paymentMapper.toDto(payment);
    }
}
