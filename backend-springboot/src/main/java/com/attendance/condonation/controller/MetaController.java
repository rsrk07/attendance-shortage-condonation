package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.CourseOptionDto;
import com.attendance.condonation.dto.MetaOptionsDto;
import com.attendance.condonation.dto.OptionDto;
import com.attendance.condonation.entity.DocumentType;
import com.attendance.condonation.entity.ReasonCategory;
import com.attendance.condonation.service.CondonationService;
import com.attendance.condonation.service.DocumentInspector;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/meta")
public class MetaController {

  private final CondonationService condonationService;

  public MetaController(CondonationService condonationService) {
    this.condonationService = condonationService;
  }

  /** Dropdown values for the request and payment forms. */
  @GetMapping("/options")
  public MetaOptionsDto options() {
    return new MetaOptionsDto(
        Arrays.stream(ReasonCategory.values()).map(r -> new OptionDto(r.name(), r.label())).toList(),
        Arrays.stream(DocumentType.values()).map(d -> new OptionDto(d.name(), d.label())).toList(),
        List.of(
            new OptionDto("UPI", "UPI"),
            new OptionDto("CARD", "Debit / credit card"),
            new OptionDto("NET_BANKING", "Net banking")),
        DocumentInspector.MAX_SIZE_BYTES,
        DocumentInspector.ALLOWED_EXTENSIONS,
        5);
  }

  /** The logged-in student's courses with attendance percentage. */
  @GetMapping("/my-courses")
  public List<CourseOptionDto> myCourses(@RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return condonationService.coursesFor(user);
  }
}
