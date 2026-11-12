package com.attendance.condonation.dto;

import com.attendance.condonation.entity.ReasonCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRequestDto(
    @NotBlank(message = "Choose a course") String courseCode,
    @NotNull(message = "Choose a reason category") ReasonCategory reasonCategory,
    @NotBlank(message = "Describe the reason for your absence")
        @Size(min = 20, max = 1000, message = "Reason must be 20 to 1000 characters")
        String reason,
    @NotEmpty(message = "Attach at least one supporting document")
        @Size(max = 5, message = "You can attach at most 5 documents")
        List<@Valid DocumentInput> documents) {}
