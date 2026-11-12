package com.attendance.condonation.repository;

import com.attendance.condonation.entity.StoredFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {}
