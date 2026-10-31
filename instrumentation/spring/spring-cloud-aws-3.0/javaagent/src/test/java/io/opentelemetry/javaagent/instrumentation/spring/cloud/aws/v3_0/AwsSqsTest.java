/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.awspring.cloud.sqs.MessageHeaderUtils;
import io.awspring.cloud.sqs.listener.AsyncMessageListener;
import io.awspring.cloud.sqs.listener.MessageProcessingContext;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementHandler;
import io.awspring.cloud.sqs.listener.pipeline.MessageListenerExecutionStage;
import io.awspring.cloud.sqs.listener.pipeline.MessageProcessingConfiguration;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
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
import org.springframework.messaging.support.MessageBuilder;

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
    AwsSqsTestApplication.batchInterceptor = null;
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
                    span.hasName("send test-queue")
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
                            equalTo(stringKey("messaging.system.name"), "aws.sqs"),
                            satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                            equalTo(
                                AWS_SQS_QUEUE_URL,
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span -> {
                  span.hasName("process test-queue")
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
                          equalTo(stringKey("messaging.system.name"), "aws.sqs"),
                          satisfies(MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
                          equalTo(MESSAGING_OPERATION_NAME, "process"),
                          equalTo(MESSAGING_OPERATION_TYPE, "process"),
                          equalTo(MESSAGING_DESTINATION_NAME, "test-queue"));
                  span.hasLinksSatisfying(
                      links ->
                          assertThat(links)
                              .singleElement()
                              .satisfies(
                                  link ->
                                      assertThat(link.getSpanContext().getSpanId())
                                          .isEqualTo(trace.getSpan(2).getSpanId())));
                },
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(3)),
                span ->
                    span.hasName("delete test-queue")
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
                            equalTo(stringKey("messaging.system.name"), "aws.sqs"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                            equalTo(MESSAGING_OPERATION_NAME, "delete"),
                            equalTo(MESSAGING_OPERATION_TYPE, "settle"),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, Long.valueOf(1)),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))));
    assertConsumedMessages();

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
                                    equalTo(stringKey("messaging.system.name"), "aws.sqs"),
                                    satisfies(
                                        MESSAGING_MESSAGE_ID, AbstractStringAssert::isNotBlank),
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

  @Test
  void sqsBatchListener() throws Exception {
    List<String> payloads = new CopyOnWriteArrayList<>();
    AwsSqsTestApplication.batchMessageHandler =
        batch -> testing.runWithSpan("callback", () -> payloads.addAll(batch));
    List<Message<Object>> messages = new CopyOnWriteArrayList<>();
    AwsSqsTestApplication.batchInterceptor = messages::addAll;

    testing.runWithSpan(
        "parent",
        () ->
            sqsTemplate.sendMany(
                "batch-queue",
                asList(
                    MessageBuilder.withPayload("hello").build(),
                    MessageBuilder.withPayload("world").build())));

    // the queue may hand out the messages in one or more polls, each is a separate batch
    await()
        .untilAsserted(
            () -> {
              assertThat(payloads).containsExactlyInAnyOrder("hello", "world");
              assertThat(sumBatchMessageCount("delete batch-queue")).isEqualTo(2);
            });

    // each message of the batch was created by its own span
    List<String> createSpanIds =
        findSpans("create batch-queue").stream().map(SpanData::getSpanId).toList();
    assertThat(createSpanIds).hasSize(2);
    List<SpanData> processSpans = findSpans("process batch-queue");
    assertThat(sumBatchMessageCount("process batch-queue")).isEqualTo(2);
    for (SpanData process : processSpans) {
      long messageCount = process.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT);
      assertThat(process.getInstrumentationScopeInfo().getName())
          .isEqualTo("io.opentelemetry.spring-cloud-aws-3.0");
      assertThat(process)
          .hasKind(SpanKind.CONSUMER)
          .hasNoParent()
          .hasAttributesSatisfyingExactly(
              equalTo(MESSAGING_SYSTEM, AWS_SQS),
              equalTo(MESSAGING_DESTINATION_NAME, "batch-queue"),
              equalTo(MESSAGING_OPERATION_NAME, "process"),
              equalTo(MESSAGING_OPERATION_TYPE, "process"),
              equalTo(MESSAGING_BATCH_MESSAGE_COUNT, messageCount))
          .hasLinksSatisfying(links -> assertLinksToCreate(links, createSpanIds, messageCount));
    }
    List<String> processSpanIds = processSpans.stream().map(SpanData::getSpanId).toList();
    assertThat(findSpans("callback"))
        .allSatisfy(span -> assertThat(span.getParentSpanId()).isIn(processSpanIds));
    assertThat(findSpans("delete batch-queue"))
        .allSatisfy(span -> assertThat(span.getParentSpanId()).isIn(processSpanIds));

    // conversion of the batch does not start per-message SDK process spans
    assertThat(testing.spans())
        .filteredOn(
            span ->
                span.getInstrumentationScopeInfo().getName().equals("io.opentelemetry.aws-sdk-2.2")
                    && span.getName().equals("process batch-queue"))
        .isEmpty();
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric
                        .getInstrumentationScopeInfo()
                        .getName()
                        .equals("io.opentelemetry.aws-sdk-2.2")
                    && metric.getName().equals("messaging.client.consumed.messages"))
        .isEmpty();
    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-cloud-aws-3.0",
        "messaging.process.duration",
        metrics -> metrics.hasSize(1));
    assertBatchConsumedMessages(2);

    AwsSqsTestApplication.batchMessageHandler = null;
    AwsSqsTestApplication.batchInterceptor = null;

    // a listener that is not invoked through the @SqsListener adapter and completes asynchronously
    assertBatchAsyncCompletion(messages, createSpanIds, /* success= */ true);
    assertBatchAsyncCompletion(messages, createSpanIds, /* success= */ false);
    // processing the same deliveries again does not count them again
    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-cloud-aws-3.0",
        "messaging.process.duration",
        metrics -> metrics.hasSize(1));
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric
                        .getInstrumentationScopeInfo()
                        .getName()
                        .equals("io.opentelemetry.spring-cloud-aws-3.0")
                    && metric.getName().equals("messaging.client.consumed.messages"))
        .isEmpty();
  }

  private static void assertBatchConsumedMessages(long expected) {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.spring-cloud-aws-3.0",
        "messaging.client.consumed.messages",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(expected)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME, "batch-queue"))))));
  }

  private static List<SpanData> findSpans(String name) {
    return testing.spans().stream().filter(span -> span.getName().equals(name)).toList();
  }

  private static long sumBatchMessageCount(String spanName) {
    return findSpans(spanName).stream()
        .mapToLong(span -> span.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT))
        .sum();
  }

  private static void assertLinksToCreate(
      List<? extends LinkData> links, List<String> createSpanIds, long messageCount) {
    assertThat(links)
        .hasSize((int) messageCount)
        .allSatisfy(
            link -> {
              assertThat(link.getSpanContext().getSpanId()).isIn(createSpanIds);
              assertThat(link.getAttributes().get(MESSAGING_MESSAGE_ID)).isNotBlank();
            });
  }

  private static void assertBatchAsyncCompletion(
      List<Message<Object>> messages, List<String> createSpanIds, boolean success) {
    testing.clearData();
    CompletableFuture<Void> result = new CompletableFuture<>();
    testing.runWithSpan("ambient", () -> processMessages(messages, unused -> result));

    assertThat(testing.spans())
        .filteredOn(span -> span.getName().equals("process batch-queue"))
        .isEmpty();
    if (success) {
      result.complete(null);
    } else {
      result.completeExceptionally(new IllegalStateException("listener failed"));
    }

    StatusData expectedStatus = success ? StatusData.unset() : StatusData.error();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("ambient").hasNoParent(),
                span ->
                    span.hasName("process batch-queue")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(expectedStatus)
                        .hasAttribute(MESSAGING_BATCH_MESSAGE_COUNT, 2L)
                        .hasLinksSatisfying(
                            links -> assertLinksToCreate(links, createSpanIds, 2))));
  }

  private static void assertConsumedMessages() {

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
                                                equalTo(
                                                    stringKey("messaging.system.name"), "aws.sqs"),
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
        MessageProcessingConfiguration.<String>builder()
            .messageListener(listener)
            .ackHandler(new AcknowledgementHandler<String>() {})
            .interceptors(emptyList())
            .build();
    return new MessageListenerExecutionStage<>(configuration)
        .process(message, MessageProcessingContext.create());
  }

  private static CompletableFuture<Collection<Message<Object>>> processMessages(
      Collection<Message<Object>> messages,
      Function<Collection<Message<Object>>, CompletableFuture<Void>> listener) {
    MessageProcessingConfiguration<Object> configuration =
        MessageProcessingConfiguration.<Object>builder()
            .messageListener(
                new AsyncMessageListener<Object>() {
                  @Override
                  public CompletableFuture<Void> onMessage(Message<Object> message) {
                    throw new UnsupportedOperationException();
                  }

                  @Override
                  public CompletableFuture<Void> onMessage(Collection<Message<Object>> messages) {
                    return listener.apply(messages);
                  }
                })
            .ackHandler(new AcknowledgementHandler<Object>() {})
            .interceptors(emptyList())
            .build();
    return new MessageListenerExecutionStage<>(configuration)
        .process(messages, MessageProcessingContext.create());
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
