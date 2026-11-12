package com.attendance.condonation.repository;

import com.attendance.condonation.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {}
