package com.attendance.condonation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record TaskDto(
    long taskKey,
    String name,
    String elementId,
    TaskKind kind,
    String approverRole,
    String requestId,
    String studentId,
    String studentName,
    String courseCode,
    String assignee,
    List<String> candidateGroups,
    OffsetDateTime createdAt) {

  public enum TaskKind {
    REVIEW,
    PAY_FEE,
    ADMIN_FIX,
    OTHER
  }
}
