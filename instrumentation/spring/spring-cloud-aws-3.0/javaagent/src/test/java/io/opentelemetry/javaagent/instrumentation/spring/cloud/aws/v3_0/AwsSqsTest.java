/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
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
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

@SuppressWarnings("deprecation") // using deprecated semconv
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    classes = AwsSqsTestApplication.class)
class AwsSqsTest {
  private static final String RECEIVE_SPANS_ENABLED =
      "otel.instrumentation.messaging.experimental.receive-spans.enabled";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static SQSRestServer sqs;

  @Autowired SqsTemplate sqsTemplate;
  @Autowired SqsAsyncClient sqsAsyncClient;

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
    assumeFalse(Boolean.getBoolean(RECEIVE_SPANS_ENABLED));

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
  void sqsListenerWithReceiveTelemetry() throws Exception {
    // this test asserts stable messaging span names/kinds and relies on idle listener polls being
    // suppressed (exactly one receive trace), which only holds under stable/v3 semconv
    assumeTrue(emitStableMessagingSemconv());
    assumeTrue(Boolean.getBoolean(RECEIVE_SPANS_ENABLED));

    String messageContent = "hello";
    CompletableFuture<String> messageFuture = new CompletableFuture<>();
    AwsSqsTestApplication.messageHandler =
        string -> testing.runWithSpan("callback", () -> messageFuture.complete(string));

    testing.runWithSpan("parent", () -> sqsTemplate.send("test-queue", messageContent));

    String result = messageFuture.get(10, SECONDS);
    assertThat(result).isEqualTo(messageContent);

    AtomicReference<SpanData> sendSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> span.hasName("Sqs.GetQueueUrl").hasKind(SpanKind.CLIENT),
              span -> span.hasName("send test-queue").hasKind(SpanKind.PRODUCER),
              span -> span.hasName("process test-queue").hasKind(SpanKind.CONSUMER),
              span -> span.hasName("callback").hasKind(SpanKind.INTERNAL),
              span -> span.hasName("Sqs.DeleteMessageBatch").hasKind(SpanKind.CLIENT));
          sendSpan.set(trace.getSpan(2));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertReceiveSpan(span, "test-queue", sendSpan.get())));
  }

  @Test
  void sqsListenerEmptyPollCreatesReceiveSpanInLegacyMode() throws Exception {
    assumeTrue(emitOldMessagingSemconv());
    assumeTrue(Boolean.getBoolean(RECEIVE_SPANS_ENABLED));

    // make sure the queue exists so the listener performs real (empty) receive polls against it
    sqsAsyncClient.createQueue(request -> request.queueName("test-queue")).get(10, SECONDS);

    // the listener continuously polls test-queue; with no message sent every poll is an empty
    // internal listener poll. In legacy mode this behavior is unchanged from main: an empty
    // internal listener poll still produces a receive span, even though stable/v3 semconv would
    // suppress it.
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(testing.spans())
                    .anySatisfy(
                        span -> {
                          assertThat(span.getName()).isEqualTo("test-queue receive");
                          assertThat(span.getKind()).isEqualTo(SpanKind.CONSUMER);
                          assertThat(span.getAttributes().get(MESSAGING_BATCH_MESSAGE_COUNT))
                              .isEqualTo(0L);
                        }));
  }

  @Test
  void directReceiveIsNotClassifiedAsListenerPoll() throws Exception {
    assumeTrue(emitStableMessagingSemconv());

    CreateQueueResponse createQueueResponse =
        sqsAsyncClient.createQueue(request -> request.queueName("direct-queue")).get(10, SECONDS);
    String queueUrl = createQueueResponse.queueUrl();
    testing.clearData();

    testing.runWithSpan(
        "parent",
        () ->
            sqsAsyncClient
                .sendMessage(request -> request.queueUrl(queueUrl).messageBody("direct"))
                .join());

    ReceiveMessageResponse response =
        sqsAsyncClient
            .receiveMessage(
                request -> request.queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(1))
            .get(10, SECONDS);
    assertThat(response.sdkHttpResponse().isSuccessful()).isTrue();

    if ("false".equals(System.getProperty(RECEIVE_SPANS_ENABLED))) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                  span -> span.hasName("send direct-queue").hasKind(SpanKind.PRODUCER)));
      return;
    }

    AtomicReference<SpanData> sendSpan = new AtomicReference<>();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
              span -> span.hasName("send direct-queue").hasKind(SpanKind.PRODUCER));
          sendSpan.set(trace.getSpan(1));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> assertReceiveSpan(span, "direct-queue", sendSpan.get())));
  }

  private static void assertReceiveSpan(
      SpanDataAssert span, String destinationName, SpanData sendSpan) {
    span.hasName("receive " + destinationName)
        .hasKind(SpanKind.CLIENT)
        .hasNoParent()
        .hasLinksSatisfying(
            links ->
                assertThat(links)
                    .singleElement()
                    .satisfies(
                        link ->
                            assertThat(link.getSpanContext().getSpanId())
                                .isEqualTo(sendSpan.getSpanId())))
        .hasAttributesSatisfying(
            equalTo(RPC_SYSTEM, "aws-api"),
            equalTo(RPC_SERVICE, "Sqs"),
            equalTo(RPC_METHOD, "ReceiveMessage"),
            equalTo(MESSAGING_SYSTEM, AWS_SQS),
            equalTo(MESSAGING_DESTINATION_NAME, destinationName),
            equalTo(MESSAGING_OPERATION_NAME, "receive"),
            equalTo(MESSAGING_OPERATION_TYPE, "receive"),
            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1L));
  }
}
