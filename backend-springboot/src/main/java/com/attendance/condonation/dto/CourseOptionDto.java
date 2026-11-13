package com.attendance.condonation.dto;

import java.math.BigDecimal;

public record CourseOptionDto(
    String courseCode,
    String courseName,
    int attended,
    int conducted,
    BigDecimal percentage,
    boolean shortage,
    boolean hasOpenRequest,
    boolean condoned) {}
