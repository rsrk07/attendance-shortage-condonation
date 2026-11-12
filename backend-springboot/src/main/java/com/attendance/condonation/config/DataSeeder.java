package com.attendance.condonation.config;

import com.attendance.condonation.entity.AppUser;
import com.attendance.condonation.entity.CourseAttendance;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.entity.Student;
import com.attendance.condonation.repository.AppUserRepository;
import com.attendance.condonation.repository.StudentRepository;
import com.attendance.condonation.service.AuthService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the student attendance dataset (the same JSON files the REST connector fetches from GitHub)
 * and creates the demo users: one per student plus fa, aa, hod and admin.
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CourseJson(String code, String name, int attended, int conducted) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record StudentJson(String studentId, String name, String department, Integer semester, List<CourseJson> courses) {}

  private record StaffUser(String username, String displayName, Role role) {}

  private static final List<StaffUser> STAFF =
      List.of(
          new StaffUser("fa", "Faculty Advisor", Role.FA),
          new StaffUser("aa", "Academic Advisor", Role.AA),
          new StaffUser("hod", "Head of Department", Role.HOD),
          new StaffUser("admin", "Academic Office Admin", Role.ADMIN));

  private final StudentRepository studentRepository;
  private final AppUserRepository userRepository;
  private final AuthService authService;
  private final AppProperties properties;
  private final ObjectMapper objectMapper;

  public DataSeeder(
      StudentRepository studentRepository,
      AppUserRepository userRepository,
      AuthService authService,
      AppProperties properties,
      ObjectMapper objectMapper) {
    this.studentRepository = studentRepository;
    this.userRepository = userRepository;
    this.authService = authService;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws Exception {
    Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:data/attendance/*.json");
    int students = 0;
    for (Resource file : files) {
      StudentJson json;
      try (InputStream in = file.getInputStream()) {
        json = objectMapper.readValue(in, StudentJson.class);
      }
      if (!studentRepository.existsById(json.studentId())) {
        String email = json.studentId().toLowerCase(Locale.ROOT) + "@" + properties.studentEmailDomain();
        Student student = new Student(json.studentId(), json.name(), json.department(), json.semester(), email);
        for (CourseJson c : json.courses()) {
          student.addCourse(new CourseAttendance(c.code(), c.name(), c.attended(), c.conducted()));
        }
        studentRepository.save(student);
        students++;
      }
      createUser(json.studentId(), json.name(), Role.STUDENT,
          json.studentId().toLowerCase(Locale.ROOT) + "@" + properties.studentEmailDomain(), json.studentId());
    }
    for (StaffUser staff : STAFF) {
      createUser(staff.username(), staff.displayName(), staff.role(), null, null);
    }
    log.info("Seed data ready: {} attendance file(s), {} new student(s), {} user(s)",
        files.length, students, userRepository.count());
  }

  private void createUser(String username, String displayName, Role role, String email, String studentId) {
    if (!userRepository.existsByUsernameIgnoreCase(username)) {
      userRepository.save(
          new AppUser(username, displayName, authService.hash(properties.demoPassword()), role, email, studentId));
    }
  }
}
