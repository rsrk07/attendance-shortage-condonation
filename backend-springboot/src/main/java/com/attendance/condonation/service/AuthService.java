package com.attendance.condonation.service;

import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.config.AppProperties;
import com.attendance.condonation.dto.LoginResponse;
import com.attendance.condonation.dto.UserDto;
import com.attendance.condonation.entity.AppUser;
import com.attendance.condonation.exception.ApiException;
import com.attendance.condonation.repository.AppUserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Simple token login for the demo users. Passwords are stored as BCrypt hashes; tokens are random,
 * kept in memory and expire after {@code app.token-ttl}.
 */
@Service
public class AuthService {

  private final AppUserRepository userRepository;
  private final AppProperties properties;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private final SecureRandom random = new SecureRandom();
  private final Map<String, AuthUser> sessions = new ConcurrentHashMap<>();

  public AuthService(AppUserRepository userRepository, AppProperties properties) {
    this.userRepository = userRepository;
    this.properties = properties;
  }

  public String hash(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }

  public LoginResponse login(String username, String password) {
    AppUser user =
        userRepository
            .findByUsernameIgnoreCase(username.trim())
            .filter(u -> passwordEncoder.matches(password, u.getPasswordHash()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password."));
    Instant expiresAt = Instant.now().plus(properties.tokenTtl());
    AuthUser session =
        new AuthUser(
            user.getUsername(),
            user.getDisplayName(),
            user.getRole(),
            user.getStudentId(),
            user.getEmail(),
            expiresAt);
    String token = newToken();
    sessions.put(token, session);
    return new LoginResponse(token, expiresAt, toDto(session));
  }

  public Optional<AuthUser> resolve(String token) {
    AuthUser user = sessions.get(token);
    if (user == null) {
      return Optional.empty();
    }
    if (user.isExpired()) {
      sessions.remove(token);
      return Optional.empty();
    }
    return Optional.of(user);
  }

  public void logout(String token) {
    sessions.remove(token);
  }

  public UserDto toDto(AuthUser user) {
    return new UserDto(
        user.username(), user.displayName(), user.role(), user.role().title(), user.studentId(), user.email());
  }

  /** Usernames and roles for the login screen (never passwords). */
  public List<UserDto> demoUsers() {
    return userRepository.findAllByOrderByRoleAscUsernameAsc().stream()
        .map(
            u ->
                new UserDto(
                    u.getUsername(), u.getDisplayName(), u.getRole(), u.getRole().title(), u.getStudentId(), null))
        .toList();
  }

  private String newToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
