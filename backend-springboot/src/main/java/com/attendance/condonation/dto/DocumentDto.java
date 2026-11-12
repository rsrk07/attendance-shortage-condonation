package com.attendance.condonation.dto;

import com.attendance.condonation.entity.DocumentType;
import com.attendance.condonation.entity.VerificationStatus;
import java.time.Instant;

public record DocumentDto(
    Long id,
    DocumentType type,
    String typeLabel,
    String description,
    String fileId,
    String fileName,
    String contentType,
    long sizeBytes,
    VerificationStatus verificationStatus,
    String verificationNote,
    Instant verifiedAt) {}
