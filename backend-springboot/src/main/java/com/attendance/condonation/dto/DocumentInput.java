package com.attendance.condonation.dto;

import com.attendance.condonation.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One row of the document array builder in the request form. */
public record DocumentInput(
    @NotNull(message = "Choose a document type") DocumentType type,
    @NotBlank(message = "Upload a file for every document") String fileId,
    @Size(max = 200, message = "Description must be at most 200 characters") String description) {}
