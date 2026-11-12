package com.attendance.condonation.dto;

import com.attendance.condonation.entity.Role;
import java.time.Instant;

public record ApprovalDto(
    Role role, String roleTitle, boolean approved, String remarks, String reviewerName, Instant decidedAt) {}
