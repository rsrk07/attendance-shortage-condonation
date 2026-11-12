package com.attendance.condonation.repository;

import com.attendance.condonation.entity.SisRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SisRecordRepository extends JpaRepository<SisRecord, Long> {

  Optional<SisRecord> findByStudentIdAndCourseCode(String studentId, String courseCode);

  List<SisRecord> findAllByOrderByUpdatedAtDesc();
}
