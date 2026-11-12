package com.attendance.condonation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A document attached to a condonation request; one entry of the {@code documents} process variable. */
@Entity
@Table(name = "supporting_document")
public class SupportingDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id")
  private CondonationRequest request;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DocumentType documentType;

  @Column(length = 200)
  private String description;

  /** Id of the {@link StoredFile} holding the uploaded bytes. */
  @Column(nullable = false, length = 36)
  private String fileId;

  @Column(nullable = false, length = 200)
  private String fileName;

  @Column(length = 100)
  private String contentType;

  private long sizeBytes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private VerificationStatus verificationStatus = VerificationStatus.PENDING;

  @Column(length = 500)
  private String verificationNote;

  private Instant verifiedAt;

  protected SupportingDocument() {}

  public SupportingDocument(DocumentType documentType, String description, StoredFile file) {
    this.documentType = documentType;
    this.description = description;
    this.fileId = file.getId();
    this.fileName = file.getFileName();
    this.contentType = file.getContentType();
    this.sizeBytes = file.getSizeBytes();
  }

  public void markVerified(String note) {
    this.verificationStatus = VerificationStatus.VERIFIED;
    this.verificationNote = note;
    this.verifiedAt = Instant.now();
  }

  public void markRejected(String note) {
    this.verificationStatus = VerificationStatus.REJECTED;
    this.verificationNote = note;
    this.verifiedAt = Instant.now();
  }

  void setRequest(CondonationRequest request) {
    this.request = request;
  }

  public Long getId() {
    return id;
  }

  public CondonationRequest getRequest() {
    return request;
  }

  public DocumentType getDocumentType() {
    return documentType;
  }

  public String getDescription() {
    return description;
  }

  public String getFileId() {
    return fileId;
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

  public VerificationStatus getVerificationStatus() {
    return verificationStatus;
  }

  public String getVerificationNote() {
    return verificationNote;
  }

  public Instant getVerifiedAt() {
    return verifiedAt;
  }
}
