package com.attendance.condonation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PaymentVerifierTest {

  private final PaymentVerifier verifier = new PaymentVerifier();

  @Test
  void acceptsFullPayment() {
    assertThat(verifier.problem(true, "UPI12345678", new BigDecimal("1000"), 1000)).isEmpty();
    assertThat(verifier.problem(true, "TXN987654321", new BigDecimal("1500.00"), 1000)).isEmpty();
  }

  @Test
  void rejectsUnderpayment() {
    assertThat(verifier.problem(true, "UPI12345678", new BigDecimal("500"), 1000))
        .hasValueSatisfying(msg -> assertThat(msg).contains("less than the fee of Rs. 1000"));
  }

  @Test
  void rejectsUnconfirmedOrBadReference() {
    assertThat(verifier.problem(false, "UPI12345678", new BigDecimal("1000"), 1000)).isPresent();
    assertThat(verifier.problem(true, "abc", new BigDecimal("1000"), 1000)).isPresent();
    assertThat(verifier.problem(true, "UPI-1234-5678", new BigDecimal("1000"), 1000)).isPresent();
  }

  @Test
  void rejectsMissingFee() {
    assertThat(verifier.problem(true, "UPI12345678", new BigDecimal("1000"), null)).isPresent();
  }
}
