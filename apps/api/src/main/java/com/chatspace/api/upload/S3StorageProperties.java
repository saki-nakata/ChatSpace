package com.chatspace.api.upload;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code chatspace.storage.type=s3}のときに使うR2(S3互換)接続設定。未設定のまま起動すると Bean
 * Validationで起動時に失敗する(実行時の分かりにくいSDK例外を避けるため、インフラ構成書§7.1)。
 */
@ConfigurationProperties(prefix = "chatspace.storage.s3")
@Validated
public class S3StorageProperties {

  @NotBlank private String bucket;
  @NotBlank private String endpoint;
  @NotBlank private String region;
  @NotBlank private String accessKeyId;
  @NotBlank private String secretAccessKey;

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getSecretAccessKey() {
    return secretAccessKey;
  }

  public void setSecretAccessKey(String secretAccessKey) {
    this.secretAccessKey = secretAccessKey;
  }
}
