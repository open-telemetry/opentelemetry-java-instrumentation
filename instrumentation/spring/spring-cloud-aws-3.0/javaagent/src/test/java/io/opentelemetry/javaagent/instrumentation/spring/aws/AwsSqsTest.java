/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.aws;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
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
                span ->
                    span.hasName("Sqs.GetQueueUrl")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "GetQueueUrl"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(
                                HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                v ->
                                    v.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(AttributeKey.stringKey("aws.queue.name"), "test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("test-queue publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "SendMessage"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(
                                HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                v ->
                                    v.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(
                                MESSAGING_SYSTEM,
                                MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                    .AWS_SQS),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                            equalTo(
                                stringKey("aws.queue.url"),
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("test-queue process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "ReceiveMessage"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(
                                HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                v ->
                                    v.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(
                                MESSAGING_SYSTEM,
                                MessagingIncubatingAttributes.MessagingSystemIncubatingValues
                                    .AWS_SQS),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue")),
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(3)),
                span ->
                    span.hasName("Sqs.DeleteMessageBatch")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "DeleteMessageBatch"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(
                                HTTP_REQUEST_METHOD, HttpAttributes.HttpRequestMethodValues.POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                v ->
                                    v.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(
                                stringKey("aws.queue.url"),
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))));
  }
}
