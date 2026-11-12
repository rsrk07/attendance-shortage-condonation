package com.attendance.condonation.entity;

public enum DocumentType {
  MEDICAL_CERTIFICATE("Medical certificate"),
  HOSPITAL_RECORD("Hospital / discharge record"),
  ON_DUTY_LETTER("On-duty (OD) letter"),
  PARENT_LETTER("Letter from parent / guardian"),
  OTHER("Other supporting document");

  private final String label;

  DocumentType(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
