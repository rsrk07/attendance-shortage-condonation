package com.attendance.condonation.service;

import com.attendance.condonation.auth.AuthUser;
import com.attendance.condonation.dto.FileUploadResponse;
import com.attendance.condonation.entity.StoredFile;
import com.attendance.condonation.exception.ApiException;
import com.attendance.condonation.repository.StoredFileRepository;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

  private final StoredFileRepository fileRepository;
  private final DocumentInspector inspector;

  public FileStorageService(StoredFileRepository fileRepository, DocumentInspector inspector) {
    this.fileRepository = fileRepository;
    this.inspector = inspector;
  }

  @Transactional
  public FileUploadResponse store(MultipartFile file, AuthUser user) {
    if (file == null || file.isEmpty()) {
      throw ApiException.badRequest("Choose a file to upload.");
    }
    String fileName = cleanName(file.getOriginalFilename());
    DocumentInspector.Result check = inspector.checkUpload(fileName, file.getSize());
    if (!check.valid()) {
      throw ApiException.badRequest(check.note());
    }
    byte[] data;
    try {
      data = file.getBytes();
    } catch (IOException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "The file could not be read.", e);
    }
    StoredFile stored =
        fileRepository.save(new StoredFile(fileName, file.getContentType(), data, sha256(data), user.username()));
    return new FileUploadResponse(stored.getId(), stored.getFileName(), stored.getContentType(), stored.getSizeBytes());
  }

  @Transactional(readOnly = true)
  public StoredFile get(String fileId) {
    return fileRepository.findById(fileId).orElseThrow(() -> ApiException.notFound("File not found."));
  }

  /** Keeps only the file name part and replaces characters that are unsafe in headers. */
  static String cleanName(String original) {
    String name = original == null ? "" : original.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    name = name.replaceAll("[^A-Za-z0-9._ ()-]", "_").trim();
    return name.length() > 150 ? name.substring(name.length() - 150) : name;
  }

  private static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
