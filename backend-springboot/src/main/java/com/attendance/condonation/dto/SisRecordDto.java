package com.attendance.condonation.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SisRecordDto(
    String studentId,
    String courseCode,
    String requestId,
    String sisReference,
    BigDecimal attendancePercentage,
    String slab,
    Integer feePaid,
    boolean examEligible,
    Instant updatedAt) {}
