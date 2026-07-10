/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pekko.http.scaladsl.Http;
import org.assertj.core.api.AbstractStringAssert;
import org.elasticmq.rest.sqs.SQSRestServer;
import org.elasticmq.rest.sqs.SQSRestServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
  @Autowired MessageListenerContainerRegistry registry;

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

  // Warmup is performed only once for the entire test class to avoid each test waiting for
  // the Spring SQS listener containers (which start asynchronously) to resolve queue URLs
  // and process an initial message before telemetry data can be reliably cleared.
  private static volatile boolean initialized = false;

  @BeforeEach
  void clearAndWarmup() throws InterruptedException {
    if (!initialized) {
      initialized = true;
      sqsTemplate.send("test-queue", "warmup");
      sqsTemplate.sendMany(
          "test-batch-queue", asList(MessageBuilder.withPayload("warmup1").build()));

      long startTime = System.currentTimeMillis();
      while (System.currentTimeMillis() - startTime < 30000) {
        long count =
            testing.spans().stream().filter(s -> s.getName().equals("test-queue process")).count();
        long countBatch =
            testing.spans().stream()
                .filter(s -> s.getName().equals("test-batch-queue process"))
                .count();
        long countDelete =
            testing.spans().stream()
                .filter(s -> s.getName().equals("Sqs.DeleteMessageBatch"))
                .count();
        if (count >= 1 && countBatch >= 1 && countDelete >= 2) {
          break;
        }
        Thread.sleep(100);
      }
    }

    testing.clearData();
    AwsSqsTestApplication.messageHandler = null;
    AwsSqsTestApplication.batchMessageHandler = null;
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
                    span.hasName("test-queue publish")
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
                            equalTo(MESSAGING_OPERATION, "publish"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue"),
                            equalTo(
                                AWS_SQS_QUEUE_URL,
                                "http://localhost:"
                                    + AwsSqsTestApplication.sqsPort
                                    + "/000000000000/test-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class))),
                span ->
                    span.hasName("test-queue process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasParent(trace.getSpan(1))
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
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-queue")),
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(2)),
                span ->
                    span.hasName("Sqs.DeleteMessageBatch")
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
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))));
  }

  @Test
  void sqsBatchListener() throws Exception {
    // Stop the container before sending messages so they accumulate in the queue, then start
    // the container again to ensure both messages are received and processed as a single batch.
    registry.getContainerById("batchContainer").stop();

    String messageContent1 = "hello";
    String messageContent2 = "hello2";
    List<String> collectedMessages = new CopyOnWriteArrayList<>();
    CompletableFuture<List<String>> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.batchMessageHandler =
        strings ->
            testing.runWithSpan(
                "callback",
                () -> {
                  collectedMessages.addAll(strings);
                  if (collectedMessages.size() >= 2) {
                    messageFuture.complete(collectedMessages);
                  }
                });

    testing.runWithSpan(
        "parent",
        () ->
            sqsTemplate.sendMany(
                "test-batch-queue",
                asList(
                    MessageBuilder.withPayload(messageContent1).build(),
                    MessageBuilder.withPayload(messageContent2).build())));

    registry.getContainerById("batchContainer").start();

    List<String> result = messageFuture.get(10, SECONDS);
    assertThat(result).containsExactlyInAnyOrder(messageContent1, messageContent2);

    AtomicReference<SpanData> producer = new AtomicReference<>();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span -> {
                  span.hasName("test-batch-queue publish")
                      .hasKind(SpanKind.PRODUCER)
                      .hasParent(trace.getSpan(0))
                      .hasAttributesSatisfyingExactly(
                          equalTo(RPC_SYSTEM, "aws-api"),
                          equalTo(RPC_METHOD, "SendMessageBatch"),
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
                          equalTo(MESSAGING_OPERATION, "publish"),
                          equalTo(MESSAGING_DESTINATION_NAME, "test-batch-queue"),
                          equalTo(
                              AWS_SQS_QUEUE_URL,
                              "http://localhost:"
                                  + AwsSqsTestApplication.sqsPort
                                  + "/000000000000/test-batch-queue"),
                          satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)));
                  producer.set(trace.getSpan(1));
                }),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("Sqs.GetQueueUrl")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
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
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test-batch-queue process")
                        .hasKind(SpanKind.CONSUMER)
                        .hasNoParent()
                        .hasLinksSatisfying(
                            links -> {
                              assertThat(links).hasSize(2);
                              assertThat(links)
                                  .satisfiesExactly(
                                      l -> {
                                        assertThat(l.getSpanContext().getTraceId())
                                            .isEqualTo(producer.get().getTraceId());
                                        assertThat(l.getSpanContext().getSpanId())
                                            .isEqualTo(producer.get().getSpanId());
                                      },
                                      l -> {
                                        assertThat(l.getSpanContext().getTraceId())
                                            .isEqualTo(producer.get().getTraceId());
                                        assertThat(l.getSpanContext().getSpanId())
                                            .isEqualTo(producer.get().getSpanId());
                                      });
                            })
                        .hasAttributesSatisfyingExactly(
                            equalTo(MESSAGING_SYSTEM, AWS_SQS),
                            equalTo(MESSAGING_OPERATION, "process"),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2L),
                            equalTo(MESSAGING_DESTINATION_NAME, "test-batch-queue")),
                span ->
                    span.hasName("callback").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("Sqs.DeleteMessageBatch")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
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
                                    + "/000000000000/test-batch-queue"),
                            satisfies(AWS_REQUEST_ID, val -> val.isInstanceOf(String.class)))));
  }
}
