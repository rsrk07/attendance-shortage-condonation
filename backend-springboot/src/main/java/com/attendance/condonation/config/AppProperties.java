package com.attendance.condonation.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings bound from the {@code app.*} keys in application.yml (values come from env). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String processId,
    boolean deployOnStartup,
    String demoPassword,
    Duration tokenTtl,
    List<String> corsOrigins,
    String feeDeadline,
    String reminderInterval,
    boolean simulateSisFailure,
    String notificationEmail,
    String studentEmailDomain,
    ApproverEmails approverEmails) {

  public record ApproverEmails(String fa, String aa, String hod) {}

  public boolean hasNotificationOverride() {
    return notificationEmail != null && !notificationEmail.isBlank();
  }
}
