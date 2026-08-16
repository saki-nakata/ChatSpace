package com.chatspace.api.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chatspace.api.message.AttachmentKind;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 添付ファイル機能定義書§9テストケース(MimeSnifferのマジックバイト判定単体テスト)に対応する。 */
class MimeSnifferTest {

  @Test
  void detectsPng() {
    byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0};
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("image/png", detection.mimeType());
    assertEquals(AttachmentKind.IMAGE, detection.kind());
    assertEquals("png", detection.extension());
  }

  @Test
  void detectsJpeg() {
    byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("image/jpeg", detection.mimeType());
    assertEquals(AttachmentKind.IMAGE, detection.kind());
  }

  @Test
  void detectsGif() {
    byte[] header = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("image/gif", detection.mimeType());
    assertEquals(AttachmentKind.IMAGE, detection.kind());
  }

  @Test
  void detectsWebp() {
    byte[] header = new byte[16];
    System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, header, 0, 4);
    System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("image/webp", detection.mimeType());
    assertEquals(AttachmentKind.IMAGE, detection.kind());
  }

  @Test
  void detectsMp4() {
    byte[] header = new byte[16];
    System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, header, 4, 4);
    System.arraycopy("isom".getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("video/mp4", detection.mimeType());
    assertEquals(AttachmentKind.VIDEO, detection.kind());
  }

  /** ftypシグネチャはISO base media file format全般に共通するため、ブランド未検証だと.m4a音声等も通ってしまう(レビュー指摘)。 */
  @Test
  void rejectsNonVideoFtypBrand() {
    byte[] header = new byte[16];
    System.arraycopy("ftyp".getBytes(StandardCharsets.US_ASCII), 0, header, 4, 4);
    System.arraycopy("M4A ".getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
    assertTrue(MimeSniffer.detect(header).isEmpty());
  }

  @Test
  void detectsWebm() {
    byte[] header = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};
    MimeSniffer.Detection detection = MimeSniffer.detect(header).orElseThrow();
    assertEquals("video/webm", detection.mimeType());
    assertEquals(AttachmentKind.VIDEO, detection.kind());
  }

  @Test
  void rejectsUnsupportedFormat() {
    byte[] header = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
    assertTrue(MimeSniffer.detect(header).isEmpty());
  }

  @Test
  void rejectsExtensionSpoofedFile() {
    // 拡張子が.pngを偽装していても、実データが対応形式のマジックバイトを持たなければ拒否される
    byte[] header = "#!/bin/sh\necho pwned".getBytes(StandardCharsets.US_ASCII);
    Optional<MimeSniffer.Detection> detection = MimeSniffer.detect(header);
    assertTrue(detection.isEmpty());
  }

  @Test
  void rejectsTooShortHeader() {
    byte[] header = {(byte) 0x89, 0x50};
    assertTrue(MimeSniffer.detect(header).isEmpty());
  }
}
