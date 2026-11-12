package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.AdminFixDto;
import com.attendance.condonation.dto.FeePaymentDto;
import com.attendance.condonation.dto.ReviewDecisionDto;
import com.attendance.condonation.dto.TaskDetailDto;
import com.attendance.condonation.dto.TaskDto;
import com.attendance.condonation.service.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Custom Tasklist API backed by Camunda user tasks. */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  @GetMapping
  public List<TaskDto> inbox(@RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return taskService.inbox(user);
  }

  @GetMapping("/{taskKey}")
  public TaskDetailDto get(@PathVariable long taskKey, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return taskService.detail(taskKey, user);
  }

  @PostMapping("/{taskKey}/claim")
  public TaskDto claim(@PathVariable long taskKey, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    return taskService.claim(taskKey, user);
  }

  /** FA / AA / HOD decision. */
  @PostMapping("/{taskKey}/review")
  public ResponseEntity<Void> review(
      @PathVariable long taskKey,
      @Valid @RequestBody ReviewDecisionDto body,
      @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    taskService.completeReview(taskKey, body, user);
    return ResponseEntity.noContent().build();
  }

  /** Student pays the condonation fee. */
  @PostMapping("/{taskKey}/pay-fee")
  public ResponseEntity<Void> payFee(
      @PathVariable long taskKey,
      @Valid @RequestBody FeePaymentDto body,
      @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    taskService.completePayFee(taskKey, body, user);
    return ResponseEntity.noContent().build();
  }

  /** Admin fixes a failed SIS update. */
  @PostMapping("/{taskKey}/admin-fix")
  public ResponseEntity<Void> adminFix(
      @PathVariable long taskKey,
      @Valid @RequestBody AdminFixDto body,
      @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    taskService.completeAdminFix(taskKey, body, user);
    return ResponseEntity.noContent().build();
  }
}
