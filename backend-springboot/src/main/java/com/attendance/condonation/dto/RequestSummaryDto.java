package com.attendance.condonation.dto;

import com.attendance.condonation.entity.ReasonCategory;
import com.attendance.condonation.entity.RequestStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record RequestSummaryDto(
    String requestId,
    String studentId,
    String studentName,
    String courseCode,
    String courseName,
    ReasonCategory reasonCategory,
    RequestStatus status,
    BigDecimal attendancePercentage,
    String slab,
    Integer fee,
    Instant submittedAt,
    Instant updatedAt) {}
