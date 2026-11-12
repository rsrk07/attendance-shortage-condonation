package com.attendance.condonation.dto;

import jakarta.validation.constraints.NotNull;

public record SettingsDto(@NotNull Boolean simulateSisFailure) {}
