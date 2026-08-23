/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HttpRequestMethodValues.POST;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_REQUEST_ID;
import static io.opentelemetry.semconv.incubating.AwsIncubatingAttributes.AWS_SQS_QUEUE_URL;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CompletableFuture;
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

@SuppressWarnings("deprecation") // using deprecated semconv
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

  @Test
  void sqsListener() throws Exception {
    String messageContent = "hello";
    CompletableFuture<String> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.messageHandler =
        string -> testing.runWithSpan("callback", () -> messageFuture.complete(string));

    testing.runWithSpan("parent", () -> sqsTemplate.send("test-queue", messageContent));

    String result = messageFuture.get(10, SECONDS);
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
                            equalTo(HTTP_REQUEST_METHOD, POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                val ->
                                    val.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv() ? "send test-queue" : "test-queue publish")
                        .hasKind(SpanKind.PRODUCER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "SendMessage"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(HTTP_REQUEST_METHOD, POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                val ->
                                    val.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(MESSAGING_SYSTEM, AWS_SQS),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "send" : null),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                            equalTo(
                                AWS_SQS_QUEUE_URL,
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span -> {
                  span.hasName(
                          emitStableMessagingSemconv()
                              ? "process test-queue"
                              : "test-queue process")
                      .hasKind(SpanKind.CONSUMER)
                      .hasParent(trace.getSpan(2))
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, "aws-api"),
                          equalTo(RPC_METHOD, "ReceiveMessage"),
                          equalTo(RPC_SERVICE, "Sqs"),
                          equalTo(HTTP_REQUEST_METHOD, POST),
                          equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                          equalTo(SERVER_ADDRESS, "localhost"),
                          equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                          satisfies(
                              URL_FULL,
                              val ->
                                  val.startsWith(
                                      "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                          equalTo(MESSAGING_SYSTEM, AWS_SQS),
                          satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                          equalTo(
                              MESSAGING_OPERATION, emitOldMessagingSemconv() ? "process" : null),
                          equalTo(
                              MESSAGING_OPERATION_NAME,
                              emitStableMessagingSemconv() ? "process" : null),
                          equalTo(
                              MESSAGING_OPERATION_TYPE,
                              emitStableMessagingSemconv() ? "process" : null),
                          equalTo(MESSAGING_DESTINATION_NAME, "test-queue"));
                  if (emitStableMessagingSemconv()) {
                    span.hasLinksSatisfying(
                        links ->
                            assertThat(links)
                                .singleElement()
                                .satisfies(
                                    link ->
                                        assertThat(link.getSpanContext().getSpanId())
                                            .isEqualTo(trace.getSpan(2).getSpanId())));
                  }
                },
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(3)),
                span ->
                    span.hasName(
                            emitStableMessagingSemconv()
                                ? "delete test-queue"
                                : "Sqs.DeleteMessageBatch")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(2))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_METHOD, "DeleteMessageBatch"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(HTTP_REQUEST_METHOD, POST),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, AwsSqsTestApplication.sqsPort),
                            satisfies(
                                URL_FULL,
                                val ->
                                    val.startsWith(
                                        "http://localhost:" + AwsSqsTestApplication.sqsPort)),
                            equalTo(
                                AWS_SQS_QUEUE_URL,
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            equalTo(
                                MESSAGING_SYSTEM, emitStableMessagingSemconv() ? AWS_SQS : null),
                            equalTo(
                                MESSAGING_DESTINATION_NAME,
                                emitStableMessagingSemconv() ? "test-queue" : null),
                            equalTo(
                                MESSAGING_OPERATION_NAME,
                                emitStableMessagingSemconv() ? "delete" : null),
                            equalTo(
                                MESSAGING_OPERATION_TYPE,
                                emitStableMessagingSemconv() ? "settle" : null),
                            equalTo(
                                MESSAGING_OPERATION,
                                emitStableMessagingSemconv() && emitOldMessagingSemconv()
                                    ? "settle"
                                    : null),
                            equalTo(
                                MESSAGING_BATCH_MESSAGE_COUNT,
                                emitStableMessagingSemconv() ? Long.valueOf(1) : null),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))));
    assertConsumedMessages();
  }

  private static void assertConsumedMessages() {
    if (!emitStableMessagingSemconv()) {
      assertThat(testing.metrics())
          .filteredOn(
              metric ->
                  metric
                          .getInstrumentationScopeInfo()
                          .getName()
                          .equals("io.opentelemetry.aws-sdk-2.2")
                      && metric.getName().startsWith("messaging."))
          .isEmpty();
      return;
    }

    // Receive telemetry is disabled by default, so the process operation owns this counter.
    testing.waitAndAssertMetrics(
        "io.opentelemetry.aws-sdk-2.2",
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(ERROR_TYPE, null),
                                                equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(
                                                    SERVER_PORT,
                                                    AwsSqsTestApplication.sqsPort))))));
  }
}
