package com.attendance.condonation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "condonation_request")
public class CondonationRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Human-friendly id, e.g. REQ-1001. Also the {@code requestId} process variable. */
  @Column(unique = true, length = 20)
  private String requestId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id")
  private Student student;

  @Column(nullable = false, length = 20)
  private String courseCode;

  @Column(length = 120)
  private String courseName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ReasonCategory reasonCategory;

  @Column(nullable = false, length = 1000)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RequestStatus status = RequestStatus.SUBMITTED;

  @Column(unique = true)
  private Long processInstanceKey;

  // Values decided by the process (script task + DMN), synced back from process variables
  @Column(precision = 5, scale = 2)
  private BigDecimal attendancePercentage;

  @Column(precision = 5, scale = 2)
  private BigDecimal shortfall;

  @Column(length = 30)
  private String slab;

  private Integer fee;

  private Boolean condonable;

  @Column(length = 1000)
  private String rejectionReason;

  @Column(length = 30)
  private String rejectionStage;

  // Fee payment (verified by the verify-payment job worker)
  @Column(length = 30)
  private String paymentMode;

  @Column(length = 40)
  private String paymentReference;

  @Column(precision = 10, scale = 2)
  private BigDecimal amountPaid;

  private Instant paymentVerifiedAt;

  // SIS update (update-sis-record job worker)
  private boolean sisUpdated;

  @Column(length = 40)
  private String sisReference;

  @Column(nullable = false)
  private Instant submittedAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant completedAt;

  @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id ASC")
  private List<SupportingDocument> documents = new ArrayList<>();

  @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("decidedAt ASC")
  private List<ApprovalRecord> approvals = new ArrayList<>();

  protected CondonationRequest() {}

  public CondonationRequest(
      Student student, String courseCode, String courseName, ReasonCategory reasonCategory, String reason) {
    this.student = student;
    this.courseCode = courseCode;
    this.courseName = courseName;
    this.reasonCategory = reasonCategory;
    this.reason = reason;
    this.submittedAt = Instant.now();
    this.updatedAt = this.submittedAt;
  }

  @PreUpdate
  void touch() {
    this.updatedAt = Instant.now();
  }

  public void addDocument(SupportingDocument document) {
    document.setRequest(this);
    documents.add(document);
  }

  public void addApproval(ApprovalRecord approval) {
    approval.setRequest(this);
    approvals.add(approval);
  }

  /** Moves the status forward; never moves a final request back to an earlier state. */
  public void advanceTo(RequestStatus next) {
    if (status.isFinal() || next.ordinal() <= status.ordinal() && !next.isFinal()) {
      return;
    }
    this.status = next;
    if (next.isFinal()) {
      this.completedAt = Instant.now();
    }
  }

  public Long getId() {
    return id;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public Student getStudent() {
    return student;
  }

  public String getCourseCode() {
    return courseCode;
  }

  public String getCourseName() {
    return courseName;
  }

  public ReasonCategory getReasonCategory() {
    return reasonCategory;
  }

  public String getReason() {
    return reason;
  }

  public RequestStatus getStatus() {
    return status;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public BigDecimal getAttendancePercentage() {
    return attendancePercentage;
  }

  public void setAttendancePercentage(BigDecimal attendancePercentage) {
    this.attendancePercentage = attendancePercentage;
  }

  public BigDecimal getShortfall() {
    return shortfall;
  }

  public void setShortfall(BigDecimal shortfall) {
    this.shortfall = shortfall;
  }

  public String getSlab() {
    return slab;
  }

  public void setSlab(String slab) {
    this.slab = slab;
  }

  public Integer getFee() {
    return fee;
  }

  public void setFee(Integer fee) {
    this.fee = fee;
  }

  public Boolean getCondonable() {
    return condonable;
  }

  public void setCondonable(Boolean condonable) {
    this.condonable = condonable;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public String getRejectionStage() {
    return rejectionStage;
  }

  public void setRejectionStage(String rejectionStage) {
    this.rejectionStage = rejectionStage;
  }

  public String getPaymentMode() {
    return paymentMode;
  }

  public void setPaymentMode(String paymentMode) {
    this.paymentMode = paymentMode;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public void setPaymentReference(String paymentReference) {
    this.paymentReference = paymentReference;
  }

  public BigDecimal getAmountPaid() {
    return amountPaid;
  }

  public void setAmountPaid(BigDecimal amountPaid) {
    this.amountPaid = amountPaid;
  }

  public Instant getPaymentVerifiedAt() {
    return paymentVerifiedAt;
  }

  public void setPaymentVerifiedAt(Instant paymentVerifiedAt) {
    this.paymentVerifiedAt = paymentVerifiedAt;
  }

  public boolean isSisUpdated() {
    return sisUpdated;
  }

  public void setSisUpdated(boolean sisUpdated) {
    this.sisUpdated = sisUpdated;
  }

  public String getSisReference() {
    return sisReference;
  }

  public void setSisReference(String sisReference) {
    this.sisReference = sisReference;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public List<SupportingDocument> getDocuments() {
    return documents;
  }

  public List<ApprovalRecord> getApprovals() {
    return approvals;
  }
}
