package com.attendance.condonation.dto;

import java.util.Map;

/** A task with its request and the process variables its screen needs. */
public record TaskDetailDto(TaskDto task, RequestDetailDto request, Map<String, Object> variables) {}
