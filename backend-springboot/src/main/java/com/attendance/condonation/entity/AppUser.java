package com.attendance.condonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 40)
  private String username;

  @Column(nullable = false, length = 120)
  private String displayName;

  @Column(nullable = false, length = 100)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Column(length = 120)
  private String email;

  /** Set only for students: the student's register number. */
  @Column(length = 20)
  private String studentId;

  protected AppUser() {}

  public AppUser(String username, String displayName, String passwordHash, Role role, String email, String studentId) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.role = role;
    this.email = email;
    this.studentId = studentId;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public String getEmail() {
    return email;
  }

  public String getStudentId() {
    return studentId;
  }
}
