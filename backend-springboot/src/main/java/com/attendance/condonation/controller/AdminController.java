package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.SettingsDto;
import com.attendance.condonation.dto.SisRecordDto;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.repository.SisRecordRepository;
import com.attendance.condonation.service.DtoMapper;
import com.attendance.condonation.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

  private final SettingsService settingsService;
  private final SisRecordRepository sisRepository;
  private final DtoMapper mapper;

  public AdminController(SettingsService settingsService, SisRecordRepository sisRepository, DtoMapper mapper) {
    this.settingsService = settingsService;
    this.sisRepository = sisRepository;
    this.mapper = mapper;
  }

  @GetMapping("/settings")
  public SettingsDto settings(@RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    user.require(Role.ADMIN);
    return settingsService.get();
  }

  /** Turn the simulated SIS failure on/off for newly submitted requests. */
  @PutMapping("/settings")
  public SettingsDto updateSettings(
      @Valid @RequestBody SettingsDto body, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    user.require(Role.ADMIN);
    return settingsService.update(body);
  }

  /** Contents of the simulated Student Information System. */
  @GetMapping("/sis-records")
  public List<SisRecordDto> sisRecords(@RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    user.require(Role.ADMIN, Role.HOD, Role.AA, Role.FA);
    return sisRepository.findAllByOrderByUpdatedAtDesc().stream().map(mapper::sis).toList();
  }
}
