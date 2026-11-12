package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.LoginRequest;
import com.attendance.condonation.dto.LoginResponse;
import com.attendance.condonation.dto.UserDto;
import com.attendance.condonation.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return authService.login(request.username(), request.password());
  }

  /** Demo accounts shown on the login page (usernames and roles only). */
  @GetMapping("/demo-users")
  public List<UserDto> demoUsers() {
    return authService.demoUsers();
  }

  @GetMapping("/me")
  public UserDto me(@RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return authService.toDto(user);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
    authService.logout(authorization.replaceFirst("(?i)^Bearer\\s+", ""));
    return ResponseEntity.noContent().build();
  }
}
