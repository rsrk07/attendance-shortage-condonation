package com.attendance.condonation.auth;

import com.attendance.condonation.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Checks the {@code Authorization: Bearer <token>} header on every /api request. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

  public static final String CURRENT_USER = "currentUser";

  private final AuthService authService;

  public AuthInterceptor(AuthService authService) {
    this.authService = authService;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }
    String header = request.getHeader("Authorization");
    Optional<AuthUser> user = Optional.empty();
    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
      user = authService.resolve(header.substring(7).trim());
    }
    if (user.isEmpty()) {
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.getWriter().write("{\"status\":401,\"message\":\"Please log in again.\"}");
      return false;
    }
    request.setAttribute(CURRENT_USER, user.get());
    return true;
  }
}
