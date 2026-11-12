package com.attendance.condonation.repository;

import com.attendance.condonation.entity.CourseAttendance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseAttendanceRepository extends JpaRepository<CourseAttendance, Long> {

  List<CourseAttendance> findByStudentStudentIdOrderByCourseCodeAsc(String studentId);

  Optional<CourseAttendance> findByStudentStudentIdAndCourseCode(String studentId, String courseCode);
}
