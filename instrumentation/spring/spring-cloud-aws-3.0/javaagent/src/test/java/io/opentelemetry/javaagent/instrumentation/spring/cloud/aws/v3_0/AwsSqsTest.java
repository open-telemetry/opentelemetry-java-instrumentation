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
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.awspring.cloud.sqs.MessageHeaderUtils;
import io.awspring.cloud.sqs.listener.AsyncMessageListener;
import io.awspring.cloud.sqs.listener.MessageProcessingContext;
import io.awspring.cloud.sqs.listener.pipeline.MessageListenerExecutionStage;
import io.awspring.cloud.sqs.listener.pipeline.MessageProcessingConfiguration;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.pekko.http.scaladsl.Http;
import org.assertj.core.api.AbstractStringAssert;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;

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

  @AfterEach
  void resetHandlers() {
    AwsSqsTestApplication.messageHandler = null;
    AwsSqsTestApplication.batchMessageHandler = null;
  }

  @Test
  void sqsListener() throws Exception {
    String messageContent = "hello";
    CompletableFuture<Message<String>> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.messageHandler =
        message -> testing.runWithSpan("callback", () -> messageFuture.complete(message));

    testing.runWithSpan("parent", () -> sqsTemplate.send("test-queue", messageContent));

    Message<String> result = messageFuture.get(10, SECONDS);
    assertThat(result.getPayload()).isEqualTo(messageContent);

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

    if (emitStableMessagingSemconv()) {
      testing.clearData();
      AwsSqsTestApplication.messageHandler = null;

      Message<String> retainedMessage = MessageHeaderUtils.addHeaderIfAbsent(result, "retry", true);
      RetriedMessageHandler handler = new RetriedMessageHandler();
      processMessage(
              retainedMessage,
              message -> {
                handler.handle();
                return completedFuture(null);
              })
          .join();

      assertThat(handler.invoked).isTrue();
      await()
          .untilAsserted(
              () ->
                  assertThat(testing.spans())
                      .singleElement()
                      .satisfies(
                          span ->
                              assertThat(span)
                                  .hasName("process test-queue")
                                  .hasKind(SpanKind.CONSUMER)
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
                                                  "http://localhost:"
                                                      + AwsSqsTestApplication.sqsPort)),
                                      equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                      satisfies(
                                          MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                                      equalTo(
                                          MESSAGING_OPERATION,
                                          emitOldMessagingSemconv() ? "process" : null),
                                      equalTo(MESSAGING_OPERATION_NAME, "process"),
                                      equalTo(MESSAGING_OPERATION_TYPE, "process"),
                                      equalTo(MESSAGING_DESTINATION_NAME, "test-queue"))));
      testing.waitAndAssertMetrics(
          "io.opentelemetry.aws-sdk-2.2",
          "messaging.process.duration",
          metrics -> metrics.hasSize(1));

      assertAsyncCompletion(retainedMessage, AsyncCompletion.SUCCESS);
      assertAsyncCompletion(retainedMessage, AsyncCompletion.ERROR);
      assertAsyncCompletion(retainedMessage, AsyncCompletion.CANCELLATION);
    }
  }

  @Test
  void batchListenerKeepsSdkProcessingFallback() throws Exception {
    CompletableFuture<List<String>> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.batchMessageHandler = messageFuture::complete;

    sqsTemplate.send("batch-queue", "hello");

    assertThat(messageFuture.get(10, SECONDS)).containsExactly("hello");
    assertThat(testing.spans())
        .filteredOn(
            span ->
                span.getName()
                    .equals(
                        emitStableMessagingSemconv()
                            ? "process batch-queue"
                            : "batch-queue process"))
        .hasSize(1);
    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .filteredOn(
                        span ->
                            span.getName()
                                .equals(
                                    emitStableMessagingSemconv()
                                        ? "delete batch-queue"
                                        : "Sqs.DeleteMessageBatch"))
                    .hasSize(1));
    if (!emitStableMessagingSemconv()) {
      return;
    }
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
                                                equalTo(MESSAGING_DESTINATION_NAME, "batch-queue"),
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(
                                                    SERVER_PORT,
                                                    AwsSqsTestApplication.sqsPort))))));
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

  private static void assertAsyncCompletion(
      Message<String> retainedMessage, AsyncCompletion completion) throws Exception {
    testing.clearData();
    AsyncRetriedMessageHandler handler = new AsyncRetriedMessageHandler();
    CompletableFuture<Message<String>> listenerResult =
        processMessage(retainedMessage, message -> handler.result);

    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("process test-queue"))
        .isEmpty();
    switch (completion) {
      case SUCCESS -> handler.result.complete(null);
      case ERROR ->
          handler.result.completeExceptionally(new IllegalStateException("listener failed"));
      case CANCELLATION -> listenerResult.cancel(false);
    }

    StatusData expectedStatus =
        completion == AsyncCompletion.SUCCESS ? StatusData.unset() : StatusData.error();
    await()
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .singleElement()
                    .satisfies(
                        span ->
                            assertThat(span)
                                .hasName("process test-queue")
                                .hasKind(SpanKind.CONSUMER)
                                .hasStatus(expectedStatus)));
  }

  private static CompletableFuture<Message<String>> processMessage(
      Message<String> message, AsyncMessageListener<String> listener) {
    MessageProcessingConfiguration<String> configuration =
        MessageProcessingConfiguration.<String>builder().messageListener(listener).build();
    return new MessageListenerExecutionStage<>(configuration)
        .process(message, MessageProcessingContext.create());
  }

  private static class RetriedMessageHandler {
    private boolean invoked;

    void handle() {
      invoked = true;
    }
  }

  private static class AsyncRetriedMessageHandler {
    private final CompletableFuture<Void> result = new CompletableFuture<>();

  }

  private enum AsyncCompletion {
    SUCCESS,
    ERROR,
    CANCELLATION
  }
}
