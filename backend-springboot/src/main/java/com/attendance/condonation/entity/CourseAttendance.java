package com.attendance.condonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** One course in a student's attendance record (same data the REST connector fetches from GitHub). */
@Entity
@Table(
    name = "course_attendance",
    uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "course_code"}))
public class CourseAttendance {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "student_id")
  private Student student;

  @Column(name = "course_code", nullable = false, length = 20)
  private String courseCode;

  @Column(nullable = false, length = 120)
  private String courseName;

  private int attended;

  private int conducted;

  protected CourseAttendance() {}

  public CourseAttendance(String courseCode, String courseName, int attended, int conducted) {
    this.courseCode = courseCode;
    this.courseName = courseName;
    this.attended = attended;
    this.conducted = conducted;
  }

  /** Attendance percentage rounded to 2 decimals, the same way the BPMN script task computes it. */
  public BigDecimal getPercentage() {
    if (conducted == 0) {
      return BigDecimal.ZERO;
    }
    return BigDecimal.valueOf(attended)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(conducted), 2, RoundingMode.HALF_EVEN);
  }

  void setStudent(Student student) {
    this.student = student;
  }

  public Long getId() {
    return id;
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

  public int getAttended() {
    return attended;
  }

  public int getConducted() {
    return conducted;
  }
}
