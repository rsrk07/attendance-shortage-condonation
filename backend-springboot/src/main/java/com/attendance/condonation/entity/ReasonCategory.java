package com.attendance.condonation.entity;

/** Reason categories. The values are the DMN input {@code reasonCategory}. */
public enum ReasonCategory {
  MEDICAL("Medical"),
  ON_DUTY("On duty (OD)"),
  FAMILY_EMERGENCY("Family emergency"),
  PERSONAL("Personal"),
  OTHER("Other");

  private final String label;

  ReasonCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
