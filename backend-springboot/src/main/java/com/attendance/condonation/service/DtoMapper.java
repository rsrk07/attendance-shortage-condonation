package com.attendance.condonation.service;

import com.attendance.condonation.dto.ApprovalDto;
import com.attendance.condonation.dto.DocumentDto;
import com.attendance.condonation.dto.RequestDetailDto;
import com.attendance.condonation.dto.RequestSummaryDto;
import com.attendance.condonation.dto.SisRecordDto;
import com.attendance.condonation.entity.ApprovalRecord;
import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.SisRecord;
import com.attendance.condonation.entity.Student;
import com.attendance.condonation.entity.SupportingDocument;
import org.springframework.stereotype.Component;

/** Entity to DTO conversion (call inside a transaction so lazy collections can load). */
@Component
public class DtoMapper {

  public RequestSummaryDto summary(CondonationRequest r) {
    Student s = r.getStudent();
    return new RequestSummaryDto(
        r.getRequestId(),
        s.getStudentId(),
        s.getName(),
        r.getCourseCode(),
        r.getCourseName(),
        r.getReasonCategory(),
        r.getStatus(),
        r.getAttendancePercentage(),
        r.getSlab(),
        r.getFee(),
        r.getSubmittedAt(),
        r.getUpdatedAt());
  }

  public RequestDetailDto detail(CondonationRequest r) {
    Student s = r.getStudent();
    return new RequestDetailDto(
        r.getRequestId(),
        s.getStudentId(),
        s.getName(),
        s.getDepartment(),
        s.getSemester(),
        r.getCourseCode(),
        r.getCourseName(),
        r.getReasonCategory(),
        r.getReasonCategory().label(),
        r.getReason(),
        r.getStatus(),
        r.getProcessInstanceKey(),
        r.getAttendancePercentage(),
        r.getShortfall(),
        r.getSlab(),
        r.getFee(),
        r.getCondonable(),
        r.getRejectionReason(),
        r.getRejectionStage(),
        r.getPaymentMode(),
        r.getPaymentReference(),
        r.getAmountPaid(),
        r.getPaymentVerifiedAt(),
        r.isSisUpdated(),
        r.getSisReference(),
        r.getSubmittedAt(),
        r.getUpdatedAt(),
        r.getCompletedAt(),
        r.getDocuments().stream().map(this::document).toList(),
        r.getApprovals().stream().map(this::approval).toList());
  }

  public DocumentDto document(SupportingDocument d) {
    return new DocumentDto(
        d.getId(),
        d.getDocumentType(),
        d.getDocumentType().label(),
        d.getDescription(),
        d.getFileId(),
        d.getFileName(),
        d.getContentType(),
        d.getSizeBytes(),
        d.getVerificationStatus(),
        d.getVerificationNote(),
        d.getVerifiedAt());
  }

  public ApprovalDto approval(ApprovalRecord a) {
    return new ApprovalDto(
        a.getRole(), a.getRole().title(), a.isApproved(), a.getRemarks(), a.getReviewerName(), a.getDecidedAt());
  }

  public SisRecordDto sis(SisRecord s) {
    return new SisRecordDto(
        s.getStudentId(),
        s.getCourseCode(),
        s.getRequestId(),
        s.getSisReference(),
        s.getAttendancePercentage(),
        s.getSlab(),
        s.getFeePaid(),
        s.isExamEligible(),
        s.getUpdatedAt());
  }
}
