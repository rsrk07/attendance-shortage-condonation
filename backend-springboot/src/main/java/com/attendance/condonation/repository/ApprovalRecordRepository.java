package com.attendance.condonation.repository;

import com.attendance.condonation.entity.ApprovalRecord;
import com.attendance.condonation.entity.Role;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

  List<ApprovalRecord> findByReviewerUsernameOrderByDecidedAtDesc(String reviewerUsername);

  long countByRole(Role role);
}
