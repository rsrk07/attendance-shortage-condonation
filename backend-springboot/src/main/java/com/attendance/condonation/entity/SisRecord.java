package com.attendance.condonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Simulated Student Information System (SIS) entry. The update-sis-record job worker writes here
 * once a condonation is approved and paid.
 */
@Entity
@Table(
    name = "sis_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_code"}))
public class SisRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "student_id", nullable = false, length = 20)
  private String studentId;

  @Column(name = "course_code", nullable = false, length = 20)
  private String courseCode;

  @Column(nullable = false, length = 20)
  private String requestId;

  @Column(nullable = false, length = 40)
  private String sisReference;

  @Column(precision = 5, scale = 2)
  private BigDecimal attendancePercentage;

  @Column(length = 30)
  private String slab;

  private Integer feePaid;

  /** Exam eligibility after condonation. */
  private boolean examEligible;

  @Column(nullable = false)
  private Instant updatedAt;

  protected SisRecord() {}

  public SisRecord(String studentId, String courseCode) {
    this.studentId = studentId;
    this.courseCode = courseCode;
  }

  public void apply(
      String requestId, String sisReference, BigDecimal attendancePercentage, String slab, Integer feePaid) {
    this.requestId = requestId;
    this.sisReference = sisReference;
    this.attendancePercentage = attendancePercentage;
    this.slab = slab;
    this.feePaid = feePaid;
    this.examEligible = true;
    this.updatedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getStudentId() {
    return studentId;
  }

  public String getCourseCode() {
    return courseCode;
  }

  public String getRequestId() {
    return requestId;
  }

  public String getSisReference() {
    return sisReference;
  }

  public BigDecimal getAttendancePercentage() {
    return attendancePercentage;
  }

  public String getSlab() {
    return slab;
  }

  public Integer getFeePaid() {
    return feePaid;
  }

  public boolean isExamEligible() {
    return examEligible;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
