/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.util.List;
import java.util.function.UnaryOperator;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

class AwsSdkTelemetryBuilderTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private int sqsPort;
  private SQSRestServer sqsRestServer;

  @BeforeEach
  void setUp() {
    sqsPort = PortUtils.findOpenPort();
    sqsRestServer = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();
  }

  @AfterEach
  void cleanUp() {
    if (sqsRestServer != null) {
      sqsRestServer.stopAndWait();
    }
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void deprecatedCapturedHeadersDoesNotTreatStarAsWildcard() {
    assertThat(capturedHeaderKeys(builder -> builder.setCapturedHeaders(singletonList("*"))))
        .isEmpty();
  }

  @Test
  void selectorStarCapturesEveryHeader() {
    assertThat(
            capturedHeaderKeys(
                builder -> builder.setHeaders(IncludeExclude.builder().setIncluded("*").build())))
        .isNotEmpty();
  }

  private List<String> capturedHeaderKeys(UnaryOperator<AwsSdkTelemetryBuilder> configure) {
    AwsSdkTelemetryBuilder telemetryBuilder =
        AwsSdkTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true);
    AwsSdkTelemetry telemetry = configure.apply(telemetryBuilder).build();

    SqsClient sqsClient =
        telemetry.wrap(
            SqsClient.builder()
                .endpointOverride(URI.create("http://localhost:" + sqsPort))
                .region(Region.AP_NORTHEAST_1)
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x")))
                .overrideConfiguration(
                    ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(telemetry.createExecutionInterceptor())
                        .build())
                .build());

    sqsClient.createQueue(builder -> builder.queueName("testSdkSqs"));
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";

    sqsClient.sendMessage(
        SendMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageBody("{\"type\": \"hello\"}")
            .messageAttributes(
                singletonMap(
                    "Test-Message-Header",
                    MessageAttributeValue.builder().dataType("String").stringValue("test").build()))
            .build());

    sqsClient.receiveMessage(
        ReceiveMessageRequest.builder()
            .queueUrl(queueUrl)
            .messageAttributeNames("Test-Message-Header")
            .build());

    return testing.spans().stream()
        .map(SpanData::getAttributes)
        .flatMap(attributes -> attributes.asMap().keySet().stream())
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }
}
