package com.school.app.fee.payment;

/** Everything the client-side checkout SDK (web Checkout.js / Android Razorpay SDK) needs to open the payment sheet. */
public record PaymentInitiateResponse(
        String gatewayOrderId,
        long amountInSmallestUnit,
        String currency,
        String gatewayKeyId
) {
}
