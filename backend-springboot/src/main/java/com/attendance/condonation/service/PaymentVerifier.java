package com.attendance.condonation.service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Business rules for verifying a condonation fee payment (used by the verify-payment worker). */
@Component
public class PaymentVerifier {

  private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9]{8,20}$");

  /** Returns the reason the payment is invalid, or empty if it is valid. */
  public Optional<String> problem(Boolean confirmed, String reference, BigDecimal amountPaid, Integer fee) {
    if (!Boolean.TRUE.equals(confirmed)) {
      return Optional.of("the student did not confirm the payment details");
    }
    if (reference == null || !REFERENCE.matcher(reference).matches()) {
      return Optional.of("the transaction reference must be 8 to 20 letters or digits");
    }
    if (fee == null) {
      return Optional.of("the condonation fee is unknown");
    }
    if (amountPaid == null || amountPaid.compareTo(BigDecimal.valueOf(fee)) < 0) {
      return Optional.of(
          "the amount paid (Rs. " + (amountPaid == null ? "0" : amountPaid.stripTrailingZeros().toPlainString())
              + ") is less than the fee of Rs. " + fee);
    }
    return Optional.empty();
  }
}
