package com.attendance.condonation.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An uploaded supporting document (kept in the database so the app needs no extra storage). */
@Entity
@Table(name = "stored_file")
public class StoredFile {

  @Id
  @Column(length = 36)
  private String id;

  @Column(nullable = false, length = 200)
  private String fileName;

  @Column(length = 100)
  private String contentType;

  private long sizeBytes;

  @Column(length = 64)
  private String sha256;

  @Column(nullable = false, length = 40)
  private String uploadedBy;

  @Column(nullable = false)
  private Instant uploadedAt;

  @Lob
  @Basic(fetch = FetchType.LAZY)
  @Column(nullable = false)
  private byte[] data;

  protected StoredFile() {}

  public StoredFile(String fileName, String contentType, byte[] data, String sha256, String uploadedBy) {
    this.id = UUID.randomUUID().toString();
    this.fileName = fileName;
    this.contentType = contentType;
    this.data = data;
    this.sizeBytes = data.length;
    this.sha256 = sha256;
    this.uploadedBy = uploadedBy;
    this.uploadedAt = Instant.now();
  }

  public String getId() {
    return id;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public byte[] getData() {
    return data;
  }
}
