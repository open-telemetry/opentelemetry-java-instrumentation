/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClient;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.common.collect.ImmutableList;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractSqsTracingTest {

  protected abstract InstrumentationExtension testing();

  protected abstract AmazonSQSAsyncClientBuilder configureClient(
      AmazonSQSAsyncClientBuilder client);

  private static int sqsPort;
  private static SQSRestServer sqsRestServer;
  private static AmazonSQSAsync sqsClient;

  @BeforeEach
  void setUp() {
    sqsPort = PortUtils.findOpenPort();
    sqsRestServer = SQSRestServerBuilder.withPort(sqsPort).withInterface("localhost").start();

    AWSStaticCredentialsProvider credentials =
        new AWSStaticCredentialsProvider(new BasicAWSCredentials("x", "x"));
    AwsClientBuilder.EndpointConfiguration endpointConfiguration =
        new AwsClientBuilder.EndpointConfiguration("http://localhost:" + sqsPort, "elasticmq");

    sqsClient =
        configureClient(AmazonSQSAsyncClient.asyncBuilder())
            .withCredentials(credentials)
            .withEndpointConfiguration(endpointConfiguration)
            .build();
  }

  @AfterEach
  void cleanUp() {
    if (sqsRestServer != null) {
      sqsRestServer.stopAndWait();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testSimpleSqsProducerConsumerServicesCaptureHeaders(boolean testCaptureHeaders) {
    sqsClient.createQueue("testSdkSqs");

    SendMessageRequest sendMessageRequest =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs", "{\"type\": \"hello\"}");

    if (testCaptureHeaders) {
      sendMessageRequest.addMessageAttributesEntry(
          "test-message-header",
          new MessageAttributeValue().withDataType("String").withStringValue("test"));
    }
    sqsClient.sendMessage(sendMessageRequest);

    ReceiveMessageRequest receiveMessageRequest =
        new ReceiveMessageRequest("http://localhost:" + sqsPort + "/000000000000/testSdkSqs");
    if (testCaptureHeaders) {
      receiveMessageRequest.withMessageAttributeNames("test-message-header");
    }
    ReceiveMessageResult receiveMessageResult = sqsClient.receiveMessage(receiveMessageRequest);

    // test different ways of iterating the messages list
    if (testCaptureHeaders) {
      for (Message unused : receiveMessageResult.getMessages()) {
        testing().runWithSpan("process child", () -> {});
      }
    } else {
      receiveMessageResult
          .getMessages()
          .forEach(message -> testing().runWithSpan("process child", () -> {}));
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
                                satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "CreateQueue"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              Arrays.asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                  equalTo(
                                      stringKey("aws.queue.url"),
                                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                  satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "SendMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(
                                      MESSAGING_SYSTEM,
                                      MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                          .AWS_SQS),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                  equalTo(MESSAGING_OPERATION, "publish"),
                                  satisfies(
                                      MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

                      if (testCaptureHeaders) {
                        attributes.add(
                            satisfies(
                                stringArrayKey("messaging.header.test_message_header"),
                                val -> val.isEqualTo(ImmutableList.of("test"))));
                      }

                      span.hasName("testSdkSqs publish")
                          .hasKind(SpanKind.PRODUCER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              Arrays.asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                  equalTo(
                                      stringKey("aws.queue.url"),
                                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                  satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "ReceiveMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(
                                      MESSAGING_SYSTEM,
                                      MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                          .AWS_SQS),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                  equalTo(MESSAGING_OPERATION, "receive"),
                                  equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

                      if (testCaptureHeaders) {
                        attributes.add(
                            satisfies(
                                stringArrayKey("messaging.header.test_message_header"),
                                val -> val.isEqualTo(ImmutableList.of("test"))));
                      }

                      span.hasName("testSdkSqs receive")
                          .hasKind(SpanKind.CONSUMER)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    },
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              Arrays.asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                  equalTo(
                                      stringKey("aws.queue.url"),
                                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                  satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "AmazonSQS"),
                                  equalTo(RPC_METHOD, "ReceiveMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort),
                                  equalTo(
                                      MESSAGING_SYSTEM,
                                      MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                          .AWS_SQS),
                                  equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                  equalTo(MESSAGING_OPERATION, "process"),
                                  satisfies(
                                      MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                  equalTo(NETWORK_PROTOCOL_VERSION, "1.1")));

                      if (testCaptureHeaders) {
                        attributes.add(
                            satisfies(
                                stringArrayKey("messaging.header.test_message_header"),
                                val -> val.isEqualTo(ImmutableList.of("test"))));
                      }
                      span.hasName("testSdkSqs process")
                          .hasKind(SpanKind.CONSUMER)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(attributes);
                    },
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasAttributes(Attributes.empty())));
  }

  @Test
  void testSimpleSqsProducerConsumerServicesWithParentSpan() {
    sqsClient.createQueue("testSdkSqs");
    SendMessageRequest sendMessageRequest =
        new SendMessageRequest(
            "http://localhost:" + sqsPort + "/000000000000/testSdkSqs", "{\"type\": \"hello\"}");
    sqsClient.sendMessage(sendMessageRequest);

    testing()
        .runWithSpan(
            "parent",
            () -> {
              ReceiveMessageResult receiveMessageResult =
                  sqsClient.receiveMessage(
                      "http://localhost:" + sqsPort + "/000000000000/testSdkSqs");
              receiveMessageResult
                  .getMessages()
                  .forEach(message -> testing().runWithSpan("process child", () -> {}));
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SQS.CreateQueue")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                equalTo(stringKey("aws.queue.name"), "testSdkSqs"),
                                satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "CreateQueue"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("testSdkSqs publish")
                            .hasKind(SpanKind.PRODUCER)
                            .hasNoParent()
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                equalTo(
                                    stringKey("aws.queue.url"),
                                    "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "AmazonSQS"),
                                equalTo(RPC_METHOD, "SendMessage"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(
                                    MESSAGING_SYSTEM,
                                    MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                        .AWS_SQS),
                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                equalTo(MESSAGING_OPERATION, "publish"),
                                satisfies(
                                    MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class)),
                                equalTo(NETWORK_PROTOCOL_VERSION, "1.1"))),
            trace -> {
              AtomicReference<SpanData> receiveSpan = new AtomicReference<>();
              AtomicReference<SpanData> processSpan = new AtomicReference<>();

              List<Consumer<SpanDataAssert>> assertions =
                  new ArrayList<>(
                      Arrays.asList(
                          span ->
                              span.hasName("parent")
                                  .hasNoParent()
                                  .hasAttributes(Attributes.empty()),
                          span ->
                              span.hasName("SQS.ReceiveMessage")
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                      equalTo(
                                          stringKey("aws.queue.url"),
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("testSdkSqs receive")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                      equalTo(
                                          stringKey("aws.queue.url"),
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(
                                          MESSAGING_SYSTEM,
                                          MessagingIncubatingAttributes
                                              .MessagingSystemIncubatingValues.AWS_SQS),
                                      equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                      equalTo(MESSAGING_OPERATION, "receive"),
                                      equalTo(
                                          MessagingIncubatingAttributes
                                              .MESSAGING_BATCH_MESSAGE_COUNT,
                                          1),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("testSdkSqs process")
                                  .hasKind(SpanKind.CONSUMER)
                                  .hasParent(receiveSpan.get())
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                      equalTo(
                                          stringKey("aws.endpoint"), "http://localhost:" + sqsPort),
                                      equalTo(
                                          stringKey("aws.queue.url"),
                                          "http://localhost:"
                                              + sqsPort
                                              + "/000000000000/testSdkSqs"),
                                      satisfies(
                                          AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)),
                                      equalTo(RPC_SYSTEM, "aws-api"),
                                      equalTo(RPC_SERVICE, "AmazonSQS"),
                                      equalTo(RPC_METHOD, "ReceiveMessage"),
                                      equalTo(HTTP_REQUEST_METHOD, "POST"),
                                      equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                      equalTo(URL_FULL, "http://localhost:" + sqsPort),
                                      equalTo(SERVER_ADDRESS, "localhost"),
                                      equalTo(SERVER_PORT, sqsPort),
                                      equalTo(
                                          MESSAGING_SYSTEM,
                                          MessagingIncubatingAttributes
                                              .MessagingSystemIncubatingValues.AWS_SQS),
                                      equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                      equalTo(MESSAGING_OPERATION, "process"),
                                      satisfies(
                                          MESSAGING_MESSAGE_ID,
                                          val -> val.isInstanceOf(String.class)),
                                      equalTo(NETWORK_PROTOCOL_VERSION, "1.1")),
                          span ->
                              span.hasName("process child")
                                  .hasParent(processSpan.get())
                                  .hasAttributes(Attributes.empty())));

              // on jdk8 the order of the "SQS.ReceiveMessage" and "testSdkSqs receive"
              // spans can vary
              if ("SQS.ReceiveMessage".equals(trace.getSpan(1).getName())) {
                receiveSpan.set(trace.getSpan(2));
                processSpan.set(trace.getSpan(3));
              } else {
                receiveSpan.set(trace.getSpan(1));
                processSpan.set(trace.getSpan(2));

                // move "SQS.ReceiveMessage" assertions to the last position
                assertions.add(assertions.remove(1));
              }

              trace.hasSpansSatisfyingExactly(assertions);
            });
  }

  @Test
  void testOnlyAddsAttributeNameOnceWhenRequestReused() {
    sqsClient.createQueue("testSdkSqs2");
    SendMessageRequest send =
        new SendMessageRequest(
            "http://localhost:$sqsPort/000000000000/testSdkSqs2", "{\"type\": \"hello\"}");
    sqsClient.sendMessage(send);
    ReceiveMessageRequest receive =
        new ReceiveMessageRequest("http://localhost:$sqsPort/000000000000/testSdkSqs2");
    sqsClient.receiveMessage(receive);
    sqsClient.sendMessage(send);
    sqsClient.receiveMessage(receive);
    assertThat(receive.getAttributeNames()).isEqualTo(ImmutableList.of("AWSTraceHeader"));
  }
}
