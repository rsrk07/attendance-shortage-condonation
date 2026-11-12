package com.attendance.condonation.dto;

import com.attendance.condonation.entity.RequestStatus;
import java.time.Instant;
import java.util.List;

/** Live status of a request, built from Camunda element instances, process variables and the database. */
public record StatusDto(
    String requestId,
    RequestStatus status,
    String processState,
    boolean live,
    String message,
    String currentStep,
    List<StepDto> steps,
    RequestDetailDto request,
    Instant refreshedAt) {}
