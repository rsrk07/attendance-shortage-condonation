package com.attendance.condonation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** Format checks only; the verify-payment job worker does the business verification. */
public record FeePaymentDto(
    @NotBlank(message = "Choose a payment mode")
        @Pattern(regexp = "UPI|CARD|NET_BANKING", message = "Payment mode must be UPI, CARD or NET_BANKING")
        String paymentMode,
    @NotBlank(message = "Enter the transaction reference")
        @Pattern(regexp = "^[A-Za-z0-9]{8,20}$", message = "Transaction reference must be 8 to 20 letters or digits")
        String paymentReference,
    @NotNull(message = "Enter the amount paid")
        @DecimalMin(value = "1", message = "Amount must be at least Rs. 1")
        @Digits(integer = 6, fraction = 2, message = "Amount must be a valid rupee amount")
        BigDecimal amountPaid,
    @NotNull @AssertTrue(message = "Confirm that the payment details are correct") Boolean paymentConfirmed) {}
