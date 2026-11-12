package com.attendance.condonation.controller;

import com.attendance.condonation.auth.AuthInterceptor;
import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.FileUploadResponse;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.entity.StoredFile;
import com.attendance.condonation.exception.ApiException;
import com.attendance.condonation.service.FileStorageService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload and download of supporting documents. */
@RestController
@RequestMapping("/api/files")
public class FileController {

  private final FileStorageService fileStorage;

  public FileController(FileStorageService fileStorage) {
    this.fileStorage = fileStorage;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public FileUploadResponse upload(
      @RequestParam("file") MultipartFile file, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    user.require(Role.STUDENT);
    return fileStorage.store(file, user);
  }

  /** Students can open their own uploads; staff can open every document. */
  @GetMapping("/{fileId}")
  public ResponseEntity<byte[]> download(
      @PathVariable String fileId, @RequestAttribute(AuthInterceptor.CURRENT_USER) AuthUser user) {
    StoredFile file = fileStorage.get(fileId);
    if (user.role() == Role.STUDENT && !file.getUploadedBy().equals(user.username())) {
      throw ApiException.forbidden("You can only open your own documents.");
    }
    MediaType type;
    try {
      type = file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(file.getContentType());
    } catch (IllegalArgumentException e) {
      type = MediaType.APPLICATION_OCTET_STREAM;
    }
    return ResponseEntity.ok()
        .contentType(type)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.getFileName()).build().toString())
        .header("X-Content-Type-Options", "nosniff")
        .body(file.getData());
  }
}
