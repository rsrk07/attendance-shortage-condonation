package com.attendance.condonation.dto;

import java.util.List;

public record MetaOptionsDto(
    List<OptionDto> reasonCategories,
    List<OptionDto> documentTypes,
    List<OptionDto> paymentModes,
    long maxFileSizeBytes,
    List<String> allowedFileTypes,
    int maxDocuments) {}
