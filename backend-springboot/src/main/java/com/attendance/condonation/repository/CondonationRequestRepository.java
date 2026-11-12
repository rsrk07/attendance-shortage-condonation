package com.attendance.condonation.repository;

import com.attendance.condonation.entity.CondonationRequest;
import com.attendance.condonation.entity.RequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondonationRequestRepository extends JpaRepository<CondonationRequest, Long> {

  @EntityGraph(attributePaths = {"student"})
  Optional<CondonationRequest> findByRequestId(String requestId);

  @EntityGraph(attributePaths = {"student"})
  Optional<CondonationRequest> findByProcessInstanceKey(Long processInstanceKey);

  @EntityGraph(attributePaths = {"student"})
  List<CondonationRequest> findByStudentStudentIdOrderBySubmittedAtDesc(String studentId);

  @EntityGraph(attributePaths = {"student"})
  List<CondonationRequest> findAllByOrderBySubmittedAtDesc();

  @EntityGraph(attributePaths = {"student"})
  List<CondonationRequest> findByStatusInOrderBySubmittedAtDesc(Collection<RequestStatus> statuses);

  boolean existsByStudentStudentIdAndCourseCodeAndStatusIn(
      String studentId, String courseCode, Collection<RequestStatus> statuses);

  Optional<CondonationRequest> findFirstByPaymentReferenceIgnoreCaseAndRequestIdNot(
      String paymentReference, String requestId);
}
