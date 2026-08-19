package com.chatspace.api.upload;

import com.chatspace.api.common.CurrentUser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 添付ファイル機能定義書§4の使用APIに対応する。 */
@RestController
public class UploadController {

  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<UploadResponse> upload(
      @RequestParam("file") MultipartFile file, @CurrentUser UUID userId) {
    AttachmentResponse attachment =
        uploadService.upload(readBytes(file), file.getOriginalFilename(), userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(new UploadResponse(attachment));
  }

  private byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @GetMapping("/uploads/{storageKey}")
  public ResponseEntity<Resource> serve(@PathVariable String storageKey, @CurrentUser UUID userId) {
    UploadedFile file = uploadService.serve(storageKey, userId);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.mimeType()))
        .header("X-Content-Type-Options", "nosniff")
        .body(file.resource());
  }
}
