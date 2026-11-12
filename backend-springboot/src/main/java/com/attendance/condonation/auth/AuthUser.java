package com.attendance.condonation.auth;

import com.attendance.condonation.entity.Role;
import com.attendance.condonation.exception.ApiException;
import java.time.Instant;
import java.util.Arrays;

/** The logged-in user attached to each authenticated API request. */
public record AuthUser(
    String username, String displayName, Role role, String studentId, String email, Instant expiresAt) {

  public boolean hasRole(Role... roles) {
    return Arrays.asList(roles).contains(role);
  }

  public void require(Role... roles) {
    if (!hasRole(roles)) {
      throw ApiException.forbidden("This action is not available for the " + role.title() + " role.");
    }
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }
}
