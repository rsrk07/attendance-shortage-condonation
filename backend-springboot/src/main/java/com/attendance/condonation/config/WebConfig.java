package com.attendance.condonation.config;

import com.attendance.condonation.auth.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AuthInterceptor authInterceptor;
  private final AppProperties properties;

  public WebConfig(AuthInterceptor authInterceptor, AppProperties properties) {
    this.authInterceptor = authInterceptor;
    this.properties = properties;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(authInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/auth/login", "/api/auth/demo-users");
  }

  /**
   * The React app normally calls the API through its own origin (Vite dev proxy or nginx), so CORS
   * is only needed when the frontend is opened from a different origin.
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/api/**")
        .allowedOriginPatterns(properties.corsOrigins().toArray(String[]::new))
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .maxAge(3600);
  }
}
