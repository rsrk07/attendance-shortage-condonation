package com.attendance.condonation.service;

import com.attendance.condonation.config.AppProperties;
import com.attendance.condonation.dto.SettingsDto;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/** Runtime demo settings the admin can change from the UI. */
@Service
public class SettingsService {

  private final AtomicBoolean simulateSisFailure;

  public SettingsService(AppProperties properties) {
    this.simulateSisFailure = new AtomicBoolean(properties.simulateSisFailure());
  }

  public SettingsDto get() {
    return new SettingsDto(simulateSisFailure.get());
  }

  public SettingsDto update(SettingsDto settings) {
    simulateSisFailure.set(Boolean.TRUE.equals(settings.simulateSisFailure()));
    return get();
  }

  public boolean simulateSisFailure() {
    return simulateSisFailure.get();
  }
}
