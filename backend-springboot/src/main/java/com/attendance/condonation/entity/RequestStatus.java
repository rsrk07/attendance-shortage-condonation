package com.attendance.condonation.entity;

public enum RequestStatus {
  /** Process started: attendance, eligibility and documents are being checked. */
  SUBMITTED,
  /** Documents verified; waiting for FA / AA / HOD. */
  UNDER_REVIEW,
  /** Approved by the HOD; the student has to pay the condonation fee. */
  AWAITING_PAYMENT,
  /** Fee verified; the SIS record is being updated. */
  PROCESSING,
  APPROVED,
  REJECTED;

  public boolean isFinal() {
    return this == APPROVED || this == REJECTED;
  }
}
