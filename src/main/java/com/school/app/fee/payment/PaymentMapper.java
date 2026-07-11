package com.school.app.fee.payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentDto toDto(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getFee().getId(),
                payment.getAmount(),
                payment.getGatewayOrderId(),
                payment.getGatewayPaymentId(),
                payment.getStatus(),
                payment.getInitiatedBy().getId(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }
}
