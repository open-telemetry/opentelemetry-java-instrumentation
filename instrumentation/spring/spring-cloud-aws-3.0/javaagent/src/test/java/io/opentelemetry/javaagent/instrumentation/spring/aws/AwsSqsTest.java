/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcMethodAssertions;
import static io.opentelemetry.instrumentation.testing.junit.rpc.RpcSemconvStabilityUtil.rpcSystemAssertion;
import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pekko.http.scaladsl.Http;
import org.assertj.core.api.AbstractStringAssert;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = AwsSqsTestApplication.class)
class AwsSqsTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static SQSRestServer sqs;

  @Autowired SqsTemplate sqsTemplate;

  @BeforeAll
  static void setUp() {
    sqs = SQSRestServerBuilder.withPort(0).withInterface("localhost").start();
    Http.ServerBinding server = sqs.waitUntilStarted();
    AwsSqsTestApplication.sqsPort = server.localAddress().getPort();
  }

  @AfterAll
  static void cleanUp() {
    if (sqs != null) {
      sqs.stopAndWait();
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Test
  void sqsListener() throws InterruptedException, ExecutionException, TimeoutException {
    String messageContent = "hello";
    CompletableFuture<String> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.messageHandler =
        string -> testing.runWithSpan("callback", () -> messageFuture.complete(string));

    testing.runWithSpan("parent", () -> sqsTemplate.send("test-queue", messageContent));

    String result = messageFuture.get(10, TimeUnit.SECONDS);
    assertThat(result).isEqualTo(messageContent);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> {
                  List<AttributeAssertion> attrs = new ArrayList<>();
                  attrs.add(rpcSystemAssertion("aws-api"));
                  attrs.addAll(rpcMethodAssertions("Sqs", "GetQueueUrl"));
                  attrs.add(
                      equalTo(
                          HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST));
                  attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                  attrs.add(equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort));
                  attrs.add(
                      satisfies(
                          URL_FULL,
                          v ->
                              v.startsWith(
                                  "http://localhost:" + AwsSqsTestApplication.sqsPort)));
                  attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                  span.hasName("Sqs.GetQueueUrl")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(attrs);
                },
                span -> {
                  List<AttributeAssertion> attrs = new ArrayList<>();
                  attrs.add(rpcSystemAssertion("aws-api"));
                  attrs.addAll(rpcMethodAssertions("Sqs", "SendMessage"));
                  attrs.add(
                      equalTo(
                          HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST));
                  attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                  attrs.add(equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort));
                  attrs.add(
                      satisfies(
                          URL_FULL,
                          v ->
                              v.startsWith(
                                  "http://localhost:" + AwsSqsTestApplication.sqsPort)));
                  attrs.add(
                      equalTo(
                          MESSAGING_SYSTEM,
                          MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS));
                  attrs.add(satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank));
                  attrs.add(equalTo(MESSAGING_OPERATION, "publish"));
                  attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "test-queue"));
                  attrs.add(
                      equalTo(
                          AWS_SQS_QUEUE_URL,
                          "http://localhost:"
                              + AwsSqsTestApplication.sqsPort
                              + "/000000000000/test-queue"));
                  attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                  span.hasName("test-queue publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(attrs);
                },
                span -> {
                  List<AttributeAssertion> attrs = new ArrayList<>();
                  attrs.add(rpcSystemAssertion("aws-api"));
                  attrs.addAll(rpcMethodAssertions("Sqs", "ReceiveMessage"));
                  attrs.add(
                      equalTo(
                          HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST));
                  attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                  attrs.add(equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort));
                  attrs.add(
                      satisfies(
                          URL_FULL,
                          v ->
                              v.startsWith(
                                  "http://localhost:" + AwsSqsTestApplication.sqsPort)));
                  attrs.add(
                      equalTo(
                          MESSAGING_SYSTEM,
                          MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS));
                  attrs.add(satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank));
                  attrs.add(equalTo(MESSAGING_OPERATION, "process"));
                  attrs.add(equalTo(MESSAGING_DESTINATION_NAME, "test-queue"));
                  span.hasName("test-queue process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(2))
                      .hasAttributesSatisfyingExactly(attrs);
                },
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(3)),
                span -> {
                  List<AttributeAssertion> attrs = new ArrayList<>();
                  attrs.add(rpcSystemAssertion("aws-api"));
                  attrs.addAll(rpcMethodAssertions("Sqs", "DeleteMessageBatch"));
                  attrs.add(
                      equalTo(
                          HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST));
                  attrs.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
                  attrs.add(equalTo(SERVER_ADDRESS, "localhost"));
                  attrs.add(equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort));
                  attrs.add(
                      satisfies(
                          URL_FULL,
                          v ->
                              v.startsWith(
                                  "http://localhost:" + AwsSqsTestApplication.sqsPort)));
                  attrs.add(
                      equalTo(
                          AWS_SQS_QUEUE_URL,
                          "http://localhost:"
                              + AwsSqsTestApplication.sqsPort
                              + "/000000000000/test-queue"));
                  attrs.add(satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                  span.hasName("Sqs.DeleteMessageBatch")
                      .hasKind(SpanKind.CLIENT)
                      .hasParent(trace.getSpan(2))
                      .hasAttributesSatisfyingExactly(attrs);
                }));
  }
}
