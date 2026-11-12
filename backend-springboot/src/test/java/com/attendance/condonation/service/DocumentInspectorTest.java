package com.attendance.condonation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DocumentInspectorTest {

  private final DocumentInspector inspector = new DocumentInspector();

  @Test
  void acceptsRealPdf() {
    byte[] pdf = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    DocumentInspector.Result result = inspector.inspect("medical.pdf", pdf);
    assertThat(result.valid()).isTrue();
    assertThat(result.note()).contains("PDF");
  }

  @Test
  void acceptsRealPngAndJpeg() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
    assertThat(inspector.inspect("scan.png", png).valid()).isTrue();
    assertThat(inspector.inspect("photo.JPG", jpeg).valid()).isTrue();
  }

  @Test
  void rejectsRenamedTextFile() {
    byte[] text = "this is not a pdf".getBytes(StandardCharsets.US_ASCII);
    DocumentInspector.Result result = inspector.inspect("certificate.pdf", text);
    assertThat(result.valid()).isFalse();
    assertThat(result.note()).contains("not a real PDF");
  }

  @Test
  void rejectsWrongExtensionEmptyAndOversizedFiles() {
    assertThat(inspector.checkUpload("notes.docx", 100).valid()).isFalse();
    assertThat(inspector.checkUpload("empty.pdf", 0).valid()).isFalse();
    assertThat(inspector.checkUpload("big.pdf", DocumentInspector.MAX_SIZE_BYTES + 1).valid()).isFalse();
    assertThat(inspector.checkUpload("ok.pdf", 1024).valid()).isTrue();
  }

  @Test
  void cleansUploadedFileNames() {
    assertThat(FileStorageService.cleanName("C:\\Users\\me\\Medical cert.pdf")).isEqualTo("Medical cert.pdf");
    assertThat(FileStorageService.cleanName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(FileStorageService.cleanName("bad\"name;.pdf")).isEqualTo("bad_name_.pdf");
  }
}
