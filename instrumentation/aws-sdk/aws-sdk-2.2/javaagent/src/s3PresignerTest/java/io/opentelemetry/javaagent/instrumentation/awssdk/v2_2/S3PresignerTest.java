/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3PresignerTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final StaticCredentialsProvider CREDENTIALS_PROVIDER =
      StaticCredentialsProvider.create(
          AwsBasicCredentials.create("my-access-key", "my-secret-key"));

  private static S3Presigner s3Presigner;

  @BeforeAll
  static void setUp() {
    // trigger adding tracing interceptor
    S3Client.builder()
        .region(Region.AP_NORTHEAST_1)
        .credentialsProvider(CREDENTIALS_PROVIDER)
        .build();

    s3Presigner =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_1)
            .credentialsProvider(CREDENTIALS_PROVIDER)
            .build();
  }

  @Test
  void testPresignGetObject() {
    s3Presigner.presignGetObject(
        GetObjectPresignRequest.builder()
            .getObjectRequest(builder -> builder.bucket("test").key("test"))
            .signatureDuration(Duration.ofDays(1))
            .build());

    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }

  @Test
  void testPresignPutObject() {
    s3Presigner.presignPutObject(
        PutObjectPresignRequest.builder()
            .putObjectRequest(builder -> builder.bucket("test").key("test"))
            .signatureDuration(Duration.ofDays(1))
            .build());

    assertThat(Span.current().getSpanContext().isValid()).isFalse();
  }
}
