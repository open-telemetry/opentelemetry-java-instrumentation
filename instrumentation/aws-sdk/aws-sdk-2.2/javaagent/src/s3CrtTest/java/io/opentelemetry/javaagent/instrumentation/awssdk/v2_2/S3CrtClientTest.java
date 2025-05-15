/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.awssdk.v2_2;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

class S3CrtClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static LocalStackContainer localStack;
  static S3AsyncClient s3Client;

  @BeforeAll
  static void setUp() {
    localStack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0.2"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("DEBUG", "1")
            .withStartupTimeout(Duration.ofMinutes(2));
    localStack.start();
    localStack.followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("test")));

    AwsCredentialsProvider credentialsProvider =
        StaticCredentialsProvider.create(
            AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey()));

    s3Client =
        S3AsyncClient.crtBuilder()
            .endpointOverride(localStack.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(credentialsProvider)
            .region(Region.of(localStack.getRegion()))
            .build();
  }

  @AfterAll
  static void cleanUp() {
    localStack.stop();
  }

  @Test
  void testCopyObject() {
    s3Client.createBucket(request -> request.bucket("bucket")).join();
    s3Client
        .putObject(
            request -> request.bucket("bucket").key("file1.txt"),
            AsyncRequestBody.fromString("file content"))
        .join();
    testing.waitForTraces(2);
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () ->
            s3Client
                .copyObject(
                    request ->
                        request
                            .sourceBucket("bucket")
                            .sourceKey("file1.txt")
                            .destinationBucket("bucket")
                            .destinationKey("file2.txt"))
                .join());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName("S3.HeadObject")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CLIENT),
                span ->
                    span.hasName("S3.CopyObject")
                        .hasParent(trace.getSpan(0))
                        .hasKind(SpanKind.CLIENT)));
  }
}
