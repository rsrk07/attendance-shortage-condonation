package com.attendance.condonation.dto;

public record FileUploadResponse(String fileId, String fileName, String contentType, long sizeBytes) {}
