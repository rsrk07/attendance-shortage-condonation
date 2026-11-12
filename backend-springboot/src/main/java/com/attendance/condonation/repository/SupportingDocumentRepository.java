package com.attendance.condonation.repository;

import com.attendance.condonation.entity.SupportingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportingDocumentRepository extends JpaRepository<SupportingDocument, Long> {

  boolean existsByFileId(String fileId);
}
