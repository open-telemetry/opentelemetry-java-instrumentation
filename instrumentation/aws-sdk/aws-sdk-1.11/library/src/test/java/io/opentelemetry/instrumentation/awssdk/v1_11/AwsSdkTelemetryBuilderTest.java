/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.function.UnaryOperator;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
    AmazonSQSAsync sqsClient = createClient(configure);
    sqsClient.createQueue("testSdkSqs");
    String queueUrl = "http://localhost:" + sqsPort + "/000000000000/testSdkSqs";

    SendMessageRequest sendMessageRequest =
        new SendMessageRequest(queueUrl, "{\"type\": \"hello\"}");
    sendMessageRequest.addMessageAttributesEntry(
        "Test-Message-Header",
        new MessageAttributeValue().withDataType("String").withStringValue("test"));
    sqsClient.sendMessage(sendMessageRequest);

    sqsClient.receiveMessage(
        new ReceiveMessageRequest(queueUrl).withMessageAttributeNames("Test-Message-Header"));

    return testing.spans().stream()
        .map(SpanData::getAttributes)
        .flatMap(attributes -> attributes.asMap().keySet().stream())
        .map(AttributeKey::getKey)
        .filter(key -> key.startsWith("messaging.header."))
        .collect(toList());
  }

  private AmazonSQSAsync createClient(UnaryOperator<AwsSdkTelemetryBuilder> configure) {
    AwsSdkTelemetryBuilder builder =
        AwsSdkTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true);
    return AmazonSQSAsyncClient.asyncBuilder()
        .withRequestHandlers(configure.apply(builder).build().createRequestHandler())
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x")))
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq"))
        .build();
  }
}
