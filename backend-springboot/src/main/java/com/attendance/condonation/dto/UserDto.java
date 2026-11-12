package com.attendance.condonation.dto;

import com.attendance.condonation.entity.Role;

public record UserDto(String username, String displayName, Role role, String roleTitle, String studentId, String email) {}
