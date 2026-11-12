package com.attendance.condonation.entity;

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
import jakarta.persistence.Table;
import java.time.Instant;

/** Audit record of one FA / AA / HOD decision. */
@Entity
@Table(name = "approval_record")
public class ApprovalRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id")
  private CondonationRequest request;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private Role role;

  private boolean approved;

  @Column(length = 1000)
  private String remarks;

  @Column(nullable = false, length = 40)
  private String reviewerUsername;

  @Column(nullable = false, length = 120)
  private String reviewerName;

  @Column(nullable = false)
  private Instant decidedAt;

  protected ApprovalRecord() {}

  public ApprovalRecord(Role role, boolean approved, String remarks, String reviewerUsername, String reviewerName) {
    this.role = role;
    this.approved = approved;
    this.remarks = remarks;
    this.reviewerUsername = reviewerUsername;
    this.reviewerName = reviewerName;
    this.decidedAt = Instant.now();
  }

  void setRequest(CondonationRequest request) {
    this.request = request;
  }

  public Long getId() {
    return id;
  }

  public CondonationRequest getRequest() {
    return request;
  }

  public Role getRole() {
    return role;
  }

  public boolean isApproved() {
    return approved;
  }

  public String getRemarks() {
    return remarks;
  }

  public String getReviewerUsername() {
    return reviewerUsername;
  }

  public String getReviewerName() {
    return reviewerName;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
