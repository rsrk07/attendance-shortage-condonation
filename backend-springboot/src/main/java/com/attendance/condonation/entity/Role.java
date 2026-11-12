package com.attendance.condonation.entity;

/** Application roles. Each staff role maps to a Camunda candidate group used by the user tasks. */
public enum Role {
  STUDENT("students", "Student"),
  FA("FA", "Faculty Advisor"),
  AA("AA", "Academic Advisor"),
  HOD("HOD", "Head of Department"),
  ADMIN("admin", "Admin");

  private final String candidateGroup;
  private final String title;

  Role(String candidateGroup, String title) {
    this.candidateGroup = candidateGroup;
    this.title = title;
  }

  public String candidateGroup() {
    return candidateGroup;
  }

  public String title() {
    return title;
  }

  public boolean isApprover() {
    return this == FA || this == AA || this == HOD;
  }

  public boolean isStaff() {
    return this != STUDENT;
  }
}
