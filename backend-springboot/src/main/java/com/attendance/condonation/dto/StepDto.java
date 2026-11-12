package com.attendance.condonation.dto;

import java.time.OffsetDateTime;

/** One step of the live status tracker. */
public record StepDto(
    String key, String label, StepState state, String detail, OffsetDateTime startedAt, OffsetDateTime completedAt) {

  public enum StepState {
    PENDING,
    ACTIVE,
    COMPLETED,
    FAILED,
    SKIPPED,
    INCIDENT
  }
}
