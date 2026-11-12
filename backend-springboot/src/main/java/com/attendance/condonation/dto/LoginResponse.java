package com.attendance.condonation.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, UserDto user) {}
