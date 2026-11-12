package com.attendance.condonation.controller;

import com.attendance.condonation.dto.ApiError;
import com.attendance.condonation.exception.ApiException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Turns exceptions into JSON errors: {status, message, fieldErrors, timestamp}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> api(ApiException e) {
    return error(e.getStatus(), e.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
      fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
    }
    e.getBindingResult().getGlobalErrors().forEach(ge -> fields.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
    String message = fields.isEmpty() ? "Please check the form." : fields.values().iterator().next();
    return error(HttpStatus.BAD_REQUEST, message, fields);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
      MissingServletRequestParameterException.class})
  public ResponseEntity<ApiError> badInput(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "The request is not valid: " + rootMessage(e), null);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException e) {
    return error(HttpStatus.PAYLOAD_TOO_LARGE, "The file is larger than 5 MB.", null);
  }

  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ApiError> multipart(MultipartException e) {
    return error(HttpStatus.BAD_REQUEST, "The upload could not be read. Please try again.", null);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> notFound(NoResourceFoundException e) {
    return error(HttpStatus.NOT_FOUND, "Not found.", null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unexpected error", e);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", null);
  }

  private static ResponseEntity<ApiError> error(HttpStatus status, String message, Map<String, String> fields) {
    return ResponseEntity.status(status).body(new ApiError(status.value(), message, fields, Instant.now()));
  }

  private static String rootMessage(Throwable e) {
    Throwable t = e;
    while (t.getCause() != null && t.getCause() != t) {
      t = t.getCause();
    }
    String m = t.getMessage();
    return m == null ? e.getClass().getSimpleName() : m.split("\n")[0];
  }
}
