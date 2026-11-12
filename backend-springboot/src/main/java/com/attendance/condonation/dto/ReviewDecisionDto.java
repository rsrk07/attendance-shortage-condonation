package com.attendance.condonation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReviewDecisionDto(
    @NotBlank(message = "Choose Approve or Reject")
        @Pattern(regexp = "APPROVED|REJECTED", message = "Decision must be APPROVED or REJECTED")
        String decision,
    @Size(max = 1000, message = "Remarks must be at most 1000 characters") String remarks) {

  public boolean approved() {
    return "APPROVED".equals(decision);
  }
}
