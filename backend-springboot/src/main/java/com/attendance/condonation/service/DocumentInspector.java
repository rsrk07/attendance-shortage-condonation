package com.attendance.condonation.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Checks an uploaded supporting document: allowed type, size, and that the file content really
 * matches its extension (PDF / PNG / JPEG signature). Used at upload time for the basic checks and
 * by the validate-document job worker for the full check.
 */
@Component
public class DocumentInspector {

  public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
  public static final List<String> ALLOWED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");

  private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

  public record Result(boolean valid, String note) {
    static Result ok(String note) {
      return new Result(true, note);
    }

    static Result fail(String note) {
      return new Result(false, note);
    }
  }

  /** Checks done when the file is uploaded (cheap, based on name and size). */
  public Result checkUpload(String fileName, long sizeBytes) {
    if (fileName == null || fileName.isBlank()) {
      return Result.fail("The file has no name.");
    }
    if (sizeBytes == 0) {
      return Result.fail("The file is empty.");
    }
    if (sizeBytes > MAX_SIZE_BYTES) {
      return Result.fail("The file is larger than 5 MB.");
    }
    if (!ALLOWED_EXTENSIONS.contains(extension(fileName))) {
      return Result.fail("Only PDF, JPG and PNG files are accepted.");
    }
    return Result.ok("Upload accepted");
  }

  /** Full verification done by the validate-document job worker. */
  public Result inspect(String fileName, byte[] data) {
    long size = data == null ? 0 : data.length;
    Result upload = checkUpload(fileName, size);
    if (!upload.valid()) {
      return Result.fail("'" + fileName + "': " + upload.note());
    }
    String ext = extension(fileName);
    boolean signatureMatches =
        switch (ext) {
          case "pdf" -> startsWith(data, PDF);
          case "png" -> startsWith(data, PNG);
          case "jpg", "jpeg" -> startsWith(data, JPEG);
          default -> false;
        };
    if (!signatureMatches) {
      return Result.fail(
          "'" + fileName + "' is not a real " + ext.toUpperCase(Locale.ROOT)
              + " file (the content does not match the file type).");
    }
    return Result.ok(ext.toUpperCase(Locale.ROOT) + ", " + humanSize(size) + " - file signature verified");
  }

  static String extension(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  static String humanSize(long bytes) {
    if (bytes < 1024) {
      return bytes + " B";
    }
    if (bytes < 1024 * 1024) {
      return (bytes / 1024) + " KB";
    }
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  private static boolean startsWith(byte[] data, byte[] prefix) {
    return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
  }
}
