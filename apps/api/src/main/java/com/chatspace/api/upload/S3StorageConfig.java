package com.chatspace.api.upload;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * {@code chatspace.storage.type=s3}のときのみ{@link S3Client}を構築する。Cloudflare R2はS3互換APIだが、
 * path-styleアドレッシングと{@code chunkedEncodingEnabled(false)}(署名不一致対策)がCloudflare公式のAWS SDK for Java
 * サンプルで案内されている(インフラ構成書§7.1)。
 */
@Configuration
@ConditionalOnProperty(prefix = "chatspace.storage", name = "type", havingValue = "s3")
@EnableConfigurationProperties(S3StorageProperties.class)
public class S3StorageConfig {

  @Bean
  public S3Client s3Client(S3StorageProperties properties) {
    return S3Client.builder()
        .endpointOverride(URI.create(properties.getEndpoint()))
        .region(Region.of(properties.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(
                    properties.getAccessKeyId(), properties.getSecretAccessKey())))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build())
        .build();
  }
}
