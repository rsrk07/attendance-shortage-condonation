package com.attendance.condonation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskDto(
    // Zeebe keys are 64-bit; send them as strings so JavaScript keeps every digit
    @JsonSerialize(using = ToStringSerializer.class) long taskKey,
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
