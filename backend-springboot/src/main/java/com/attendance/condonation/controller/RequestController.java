package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.CreateRequestDto;
import com.attendance.condonation.dto.RequestDetailDto;
import com.attendance.condonation.dto.RequestSummaryDto;
import com.attendance.condonation.dto.StatusDto;
import com.attendance.condonation.entity.RequestStatus;
import com.attendance.condonation.service.CondonationService;
import com.attendance.condonation.service.StatusTrackerService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

  private final CondonationService condonationService;
  private final StatusTrackerService statusTracker;

  public RequestController(CondonationService condonationService, StatusTrackerService statusTracker) {
    this.condonationService = condonationService;
    this.statusTracker = statusTracker;
  }

  /** Student submits a condonation request with its supporting documents; starts the BPMN process. */
  @PostMapping
  public ResponseEntity<RequestDetailDto> create(
      @Valid @RequestBody CreateRequestDto body,
      @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    RequestDetailDto created = condonationService.submit(body, user);
    return ResponseEntity.created(URI.create("/api/requests/" + created.requestId())).body(created);
  }

  /** Students get their own requests; staff get all requests (optionally filtered by status). */
  @GetMapping
  public List<RequestSummaryDto> list(
      @RequestParam(required = false) RequestStatus status,
      @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return condonationService.list(user, status);
  }

  @GetMapping("/{requestId}")
  public RequestDetailDto get(
      @PathVariable String requestId, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return condonationService.detail(requestId, user);
  }

  /** Live status tracker (polled by the frontend). */
  @GetMapping("/{requestId}/status")
  public StatusDto status(
      @PathVariable String requestId, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return statusTracker.track(requestId, user);
  }
}
