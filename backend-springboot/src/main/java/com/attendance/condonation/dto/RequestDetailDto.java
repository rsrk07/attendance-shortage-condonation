package com.attendance.condonation.dto;

import com.attendance.condonation.entity.ReasonCategory;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.attendance.condonation.entity.RequestStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RequestDetailDto(
    String requestId,
    String studentId,
    String studentName,
    String department,
    Integer semester,
    String courseCode,
    String courseName,
    ReasonCategory reasonCategory,
    String reasonCategoryLabel,
    String reason,
    RequestStatus status,
    @JsonSerialize(using = ToStringSerializer.class) Long processInstanceKey,
    BigDecimal attendancePercentage,
    BigDecimal shortfall,
    String slab,
    Integer fee,
    Boolean condonable,
    String rejectionReason,
    String rejectionStage,
    String paymentMode,
    String paymentReference,
    BigDecimal amountPaid,
    Instant paymentVerifiedAt,
    boolean sisUpdated,
    String sisReference,
    Instant submittedAt,
    Instant updatedAt,
    Instant completedAt,
    List<DocumentDto> documents,
    List<ApprovalDto> approvals) {}
