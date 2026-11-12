package com.attendance.condonation.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminFixDto(
    @NotBlank(message = "Describe what you fixed") @Size(max = 1000) String adminNote,
    @NotNull @AssertTrue(message = "Tick the box once the SIS record is fixed") Boolean sisRecordFixed) {}
