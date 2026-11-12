package com.attendance.condonation.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "student")
public class Student {

  /** Register number, e.g. RA2311004010101. */
  @Id
  @Column(length = 20)
  private String studentId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 120)
  private String department;

  private Integer semester;

  @Column(length = 120)
  private String email;

  @OneToMany(mappedBy = "student", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("courseCode ASC")
  private List<CourseAttendance> courses = new ArrayList<>();

  protected Student() {}

  public Student(String studentId, String name, String department, Integer semester, String email) {
    this.studentId = studentId;
    this.name = name;
    this.department = department;
    this.semester = semester;
    this.email = email;
  }

  public void addCourse(CourseAttendance course) {
    course.setStudent(this);
    courses.add(course);
  }

  public String getStudentId() {
    return studentId;
  }

  public String getName() {
    return name;
  }

  public String getDepartment() {
    return department;
  }

  public Integer getSemester() {
    return semester;
  }

  public String getEmail() {
    return email;
  }

  public List<CourseAttendance> getCourses() {
    return courses;
  }
}
