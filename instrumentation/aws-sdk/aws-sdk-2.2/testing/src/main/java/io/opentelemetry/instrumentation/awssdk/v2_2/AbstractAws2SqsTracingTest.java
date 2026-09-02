/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.message.MessageHeaderUtil.headerAttributeKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
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
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.internal.shaded.guava.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractAws2SqsTracingTest extends AbstractAws2SqsBaseTest {

  private static final int WITH_PARENT_SPAN_COUNT = 5;

  @Override
  protected void assertSqsTraces(boolean withParent, boolean captureHeaders) {
    // without an ambient span the process span is parented to the message creation context, which
    // puts it in the same trace as the publish span
    boolean processInPublishTrace = emitStableMessagingSemconv() && !withParent;
    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    getTesting()
        .waitAndAssertTraces(
            trace -> trace.hasSpansSatisfyingExactly(span -> createQueueSpan(span)),
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(
                  span -> {
                    publishSpan.set(trace.getSpan(0));
                    assertPublishSpan(span, captureHeaders);
                  });

              if (processInPublishTrace) {
                spanAsserts.add(
                    span ->
                        assertProcessSpan(
                            span, trace.getSpan(0), publishSpan.get(), captureHeaders));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0));
              }

              trace.hasSpansSatisfyingExactly(spanAsserts);
            },
            trace -> {
              if (withParent) {
                SpanData parentSpan = findSpan(trace, "parent");
                SpanData receiveSpan =
                    findSpan(
                        trace,
                        emitStableMessagingSemconv() ? "receive testSdkSqs" : "testSdkSqs receive");
                SpanData processSpan =
                    findSpan(
                        trace,
                        emitStableMessagingSemconv() ? "process testSdkSqs" : "testSdkSqs process");
                trace.hasSpansSatisfyingExactlyInAnyOrder(
                    span -> span.hasName("parent").hasNoParent(),
                    span ->
                        span.hasName("Sqs.ReceiveMessage")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(parentSpan)
                            .hasTotalRecordedLinks(0)
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(
                                    AWS_SQS_QUEUE_URL,
                                    "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                                satisfies(
                                    AWS_REQUEST_ID,
                                    val ->
                                        val.matches(
                                            "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "Sqs"),
                                equalTo(RPC_METHOD, "ReceiveMessage"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                satisfies(
                                    URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort)),
                    span -> {
                      span.hasParent(parentSpan);
                      assertReceiveSpan(span, publishSpan.get(), captureHeaders);
                    },
                    span ->
                        assertProcessSpan(
                            span,
                            emitStableMessagingSemconv() ? parentSpan : receiveSpan,
                            publishSpan.get(),
                            captureHeaders),
                    span -> {
                      span.hasName("process child")
                          .hasParent(processSpan)
                          .hasTotalAttributeCount(0);
                    });
                return;
              }

              List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
              spanAsserts.add(
                  span -> {
                    span.hasNoParent();
                    assertReceiveSpan(span, publishSpan.get(), captureHeaders);
                  });
              if (!processInPublishTrace) {
                spanAsserts.add(
                    span ->
                        assertProcessSpan(
                            span, trace.getSpan(0), publishSpan.get(), captureHeaders));
                spanAsserts.add(
                    span ->
                        span.hasName("process child")
                            .hasParent(trace.getSpan(1))
                            .hasTotalAttributeCount(0));
              }

              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  // the receive span now covers the whole poll, so it starts at the same time as the http client
  // span and the two cannot be asserted in a fixed order
  private static SpanData findSpan(TraceAssert trace, String name) {
    for (int i = 0; i < WITH_PARENT_SPAN_COUNT; i++) {
      SpanData span = trace.getSpan(i);
      if (name.equals(span.getName())) {
        return span;
      }
    }
    throw new AssertionError("Span not found: " + name);
  }

  private void assertPublishSpan(SpanDataAssert span, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(
                    AWS_SQS_QUEUE_URL, "http://localhost:" + sqsPort + "/000000000000/testSdkSqs"),
                satisfies(
                    AWS_REQUEST_ID,
                    val -> val.matches("\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "Sqs"),
                equalTo(RPC_METHOD, "SendMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                satisfies(URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "send"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "send"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "publish"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(
              headerAttributeKey("Test-Message-Header"),
              val -> val.isEqualTo(ImmutableList.of("test"))));
    }

    span.hasName(emitStableMessagingSemconv() ? "send testSdkSqs" : "testSdkSqs publish")
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .hasAttributesSatisfyingExactly(attributes);
  }

  private void assertReceiveSpan(
      SpanDataAssert span, SpanData creationContext, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "Sqs"),
                equalTo(RPC_METHOD, "ReceiveMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                satisfies(URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 1)));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "receive"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "receive"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "receive"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(
              headerAttributeKey("Test-Message-Header"),
              val -> val.isEqualTo(ImmutableList.of("test"))));
    }

    span.hasName(emitStableMessagingSemconv() ? "receive testSdkSqs" : "testSdkSqs receive")
        .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
        .hasAttributesSatisfyingExactly(attributes);

    if (emitStableMessagingSemconv()) {
      span.hasLinksSatisfying(
          links ->
              assertThat(links)
                  .singleElement()
                  .satisfies(
                      link ->
                          assertThat(link.getSpanContext().getSpanId())
                              .isEqualTo(creationContext.getSpanId())));
    } else {
      span.hasTotalRecordedLinks(0);
    }
  }

  private void assertProcessSpan(
      SpanDataAssert span, SpanData parent, SpanData creationContext, boolean captureHeaders) {
    List<AttributeAssertion> attributes =
        new ArrayList<>(
            asList(
                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                equalTo(RPC_SYSTEM, "aws-api"),
                equalTo(RPC_SERVICE, "Sqs"),
                equalTo(RPC_METHOD, "ReceiveMessage"),
                equalTo(HTTP_REQUEST_METHOD, "POST"),
                equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                satisfies(URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                equalTo(SERVER_ADDRESS, "localhost"),
                equalTo(SERVER_PORT, sqsPort),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                satisfies(MESSAGING_MESSAGE_ID, val -> val.isInstanceOf(String.class))));

    if (emitStableMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION_NAME, "process"));
      attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "process"));
    }
    if (emitOldMessagingSemconv()) {
      attributes.add(equalTo(MESSAGING_OPERATION, "process"));
    }

    if (captureHeaders) {
      attributes.add(
          satisfies(
              headerAttributeKey("Test-Message-Header"),
              val -> val.isEqualTo(singletonList("test"))));
    }

    span.hasName(emitStableMessagingSemconv() ? "process testSdkSqs" : "testSdkSqs process")
        .hasKind(SpanKind.CONSUMER)
        .hasAttributesSatisfyingExactly(attributes);

    if (parent != null) {
      span.hasParent(parent);
    } else {
      span.hasNoParent();
    }

    if (creationContext != null) {
      span.hasLinksSatisfying(
          links ->
              assertThat(links)
                  .singleElement()
                  .satisfies(
                      link ->
                          assertThat(link.getSpanContext().getSpanId())
                              .isEqualTo(creationContext.getSpanId())));
    } else {
      span.hasTotalRecordedLinks(0);
    }
  }

  private static void assertCreateSpan(SpanDataAssert span) {
    span.hasName("create testSdkSqs")
        .hasKind(SpanKind.PRODUCER)
        .hasNoParent()
        .satisfies(
            spanData ->
                assertThat(spanData.getEndEpochNanos()).isEqualTo(spanData.getStartEpochNanos()))
        .hasAttributesSatisfyingExactly(
            equalTo(MESSAGING_SYSTEM, AWS_SQS),
            equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
            equalTo(MESSAGING_OPERATION_NAME, "create"),
            equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "create" : null),
            equalTo(MESSAGING_OPERATION_TYPE, "create"));
  }

  @Test
  void testCaptureMessageHeaderAsAttributeSpan() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);

    Map<String, MessageAttributeValue> attributes = new HashMap<>();
    attributes.put(
        "Test-Message-Header",
        MessageAttributeValue.builder().dataType("String").stringValue("test").build());
    attributes.put(
        "Uncaptured-Header",
        MessageAttributeValue.builder().dataType("String").stringValue("password").build());
    SendMessageRequest newSendMessageRequest =
        sendMessageRequest.toBuilder().messageAttributes(attributes).build();
    client.sendMessage(newSendMessageRequest);

    ReceiveMessageRequest newReceiveMessageRequest =
        receiveMessageRequest.toBuilder().messageAttributeNames("Test-Message-Header").build();
    ReceiveMessageResponse response = client.receiveMessage(newReceiveMessageRequest);

    assertThat(response.messages()).hasSize(1);

    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));
    assertSqsTraces(false, true);
  }

  @Test
  void testReceiveSpanLinksToProducer() {
    assumeTrue(emitStableMessagingSemconv());
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessage(sendMessageRequest);
    ReceiveMessageResponse response = client.receiveMessage(receiveMessageRequest);
    String messageId = response.messages().get(0).messageId();
    // iterating the returned message list is what starts the process spans
    response.messages().forEach(unused -> {});

    AtomicReference<SpanData> publishSpan = new AtomicReference<>();
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      publishSpan.set(trace.getSpan(0));
                      span.hasName("send testSdkSqs").hasKind(SpanKind.PRODUCER);
                    },
                    // the process span accounts for a single message, so its link carries no
                    // per-message attributes
                    span ->
                        span.hasName("process testSdkSqs")
                            .hasKind(SpanKind.CONSUMER)
                            .hasParent(trace.getSpan(0))
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link -> assertBareLink(link, publishSpan.get())))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("receive testSdkSqs")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasLinksSatisfying(
                                links ->
                                    assertThat(links)
                                        .singleElement()
                                        .satisfies(
                                            link ->
                                                assertMessageLink(
                                                    link, publishSpan.get(), messageId)))));
  }

  @Test
  void testBatchSendMessageCount() {
    assumeTrue(emitStableMessagingSemconv());
    assumeTrue(canInjectBatchCreationContext());
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    List<SendMessageBatchRequestEntry> entries = new ArrayList<>(sendMessageBatchRequest.entries());
    entries.set(
        0, entries.get(0).toBuilder().messageAttributes(dummyMessageAttributes(10)).build());
    SendMessageBatchRequest batchRequest =
        sendMessageBatchRequest.toBuilder().entries(entries).build();
    client.sendMessageBatch(batchRequest);

    assertThat(batchRequest.entries().get(0).messageAttributes()).hasSize(10);

    List<SpanData> createSpans = new ArrayList<>();
    int expectedCreateSpans = isXrayInjectionEnabled() && supportsMessageSystemAttributes() ? 3 : 2;
    List<Consumer<TraceAssert>> traceAsserts = new ArrayList<>();
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)));
    for (int i = 0; i < expectedCreateSpans; i++) {
      traceAsserts.add(
          trace -> {
            createSpans.add(trace.getSpan(0));
            trace.hasSpansSatisfyingExactly(AbstractAws2SqsTracingTest::assertCreateSpan);
          });
    }
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send testSdkSqs")
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                            equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                            satisfies(
                                AWS_REQUEST_ID,
                                val ->
                                    val.matches(
                                        "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                            equalTo(RPC_SYSTEM, "aws-api"),
                            equalTo(RPC_SERVICE, "Sqs"),
                            equalTo(RPC_METHOD, "SendMessageBatch"),
                            equalTo(HTTP_REQUEST_METHOD, "POST"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            satisfies(
                                URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, sqsPort),
                            equalTo(MESSAGING_SYSTEM, AWS_SQS),
                            equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                            equalTo(MESSAGING_OPERATION_NAME, "send"),
                            equalTo(MESSAGING_OPERATION_TYPE, "send"),
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                            equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3))
                        .hasLinksSatisfying(
                            links ->
                                assertThat(links)
                                    .extracting(link -> link.getSpanContext().getSpanId())
                                    .containsExactlyInAnyOrderElementsOf(
                                        createSpans.stream()
                                            .map(SpanData::getSpanId)
                                            .collect(toList())))));
    getTesting().waitAndAssertTraces(traceAsserts);
  }

  @Test
  void testDeleteMessage() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    String receiptHandle = createMessages(client, 1).get(0);

    client.deleteMessage(
        DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(receiptHandle).build());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID,
                                      val ->
                                          val.matches(
                                              "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "Sqs"),
                                  equalTo(RPC_METHOD, "DeleteMessage"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      URL_FULL,
                                      val -> val.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort)));
                      if (emitStableMessagingSemconv()) {
                        attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                        attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                        attributes.add(equalTo(MESSAGING_OPERATION_NAME, "delete"));
                        attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
                        attributes.add(
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "settle" : null));
                      }

                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "delete testSdkSqs"
                                  : "Sqs.DeleteMessage")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testDeleteMessageBatch() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    List<String> receiptHandles = createMessages(client, 2);

    client.deleteMessageBatch(
        DeleteMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(
                asList(
                    DeleteMessageBatchRequestEntry.builder()
                        .id("i1")
                        .receiptHandle(receiptHandles.get(0))
                        .build(),
                    DeleteMessageBatchRequestEntry.builder()
                        .id("i2")
                        .receiptHandle(receiptHandles.get(1))
                        .build()))
            .build());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID,
                                      val ->
                                          val.matches(
                                              "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "Sqs"),
                                  equalTo(RPC_METHOD, "DeleteMessageBatch"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      URL_FULL,
                                      val -> val.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort)));
                      if (emitStableMessagingSemconv()) {
                        attributes.add(equalTo(MESSAGING_SYSTEM, AWS_SQS));
                        attributes.add(equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"));
                        attributes.add(equalTo(MESSAGING_OPERATION_NAME, "delete"));
                        attributes.add(equalTo(MESSAGING_OPERATION_TYPE, "settle"));
                        attributes.add(
                            equalTo(
                                MESSAGING_OPERATION, emitOldMessagingSemconv() ? "settle" : null));
                        attributes.add(equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 2));
                      }

                      span.hasName(
                              emitStableMessagingSemconv()
                                  ? "delete testSdkSqs"
                                  : "Sqs.DeleteMessageBatch")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testChangeMessageVisibilityUsesGenericRpcSpan() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    String receiptHandle = createMessages(client, 1).get(0);

    client.changeMessageVisibility(
        ChangeMessageVisibilityRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandle)
            .visibilityTimeout(1)
            .build());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID,
                                      val ->
                                          val.matches(
                                              "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "Sqs"),
                                  equalTo(RPC_METHOD, "ChangeMessageVisibility"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      URL_FULL,
                                      val -> val.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort)));

                      span.hasName("Sqs.ChangeMessageVisibility")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testChangeMessageVisibilityBatchUsesGenericRpcSpan() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    List<String> receiptHandles = createMessages(client, 2);

    client.changeMessageVisibilityBatch(
        ChangeMessageVisibilityBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(
                asList(
                    ChangeMessageVisibilityBatchRequestEntry.builder()
                        .id("i1")
                        .receiptHandle(receiptHandles.get(0))
                        .visibilityTimeout(1)
                        .build(),
                    ChangeMessageVisibilityBatchRequestEntry.builder()
                        .id("i2")
                        .receiptHandle(receiptHandles.get(1))
                        .visibilityTimeout(1)
                        .build()))
            .build());

    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> {
                      List<AttributeAssertion> attributes =
                          new ArrayList<>(
                              asList(
                                  equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                  equalTo(AWS_SQS_QUEUE_URL, queueUrl),
                                  satisfies(
                                      AWS_REQUEST_ID,
                                      val ->
                                          val.matches(
                                              "\\s*00000000-0000-0000-0000-000000000000\\s*|UNKNOWN")),
                                  equalTo(RPC_SYSTEM, "aws-api"),
                                  equalTo(RPC_SERVICE, "Sqs"),
                                  equalTo(RPC_METHOD, "ChangeMessageVisibilityBatch"),
                                  equalTo(HTTP_REQUEST_METHOD, "POST"),
                                  equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                                  satisfies(
                                      URL_FULL,
                                      val -> val.startsWith("http://localhost:" + sqsPort)),
                                  equalTo(SERVER_ADDRESS, "localhost"),
                                  equalTo(SERVER_PORT, sqsPort)));

                      span.hasName("Sqs.ChangeMessageVisibilityBatch")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(attributes);
                    }));
  }

  @Test
  void testDeleteMessageError() {
    assumeTrue(emitStableMessagingSemconv());
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    String missingQueueUrl = "http://localhost:" + sqsPort + "/000000000000/missing";

    Throwable error =
        catchThrowable(
            () ->
                client.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(missingQueueUrl)
                        .receiptHandle("receipt-handle")
                        .build()));

    assertThat(error).isInstanceOf(QueueDoesNotExistException.class);
    getTesting()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("delete missing")
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                                equalTo(AWS_SQS_QUEUE_URL, missingQueueUrl),
                                equalTo(RPC_SYSTEM, "aws-api"),
                                equalTo(RPC_SERVICE, "Sqs"),
                                equalTo(RPC_METHOD, "DeleteMessage"),
                                equalTo(HTTP_REQUEST_METHOD, "POST"),
                                satisfies(
                                    URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                                equalTo(SERVER_ADDRESS, "localhost"),
                                equalTo(SERVER_PORT, sqsPort),
                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                equalTo(MESSAGING_DESTINATION_NAME, "missing"),
                                equalTo(MESSAGING_OPERATION_NAME, "delete"),
                                equalTo(MESSAGING_OPERATION_TYPE, "settle"),
                                equalTo(
                                    MESSAGING_OPERATION,
                                    emitOldMessagingSemconv() ? "settle" : null),
                                equalTo(ERROR_TYPE, QueueDoesNotExistException.class.getName()))));
  }

  private List<String> createMessages(SqsClient client, int count) {
    client.createQueue(createQueueRequest);
    for (int i = 0; i < count; i++) {
      client.sendMessage(sendMessageRequest.toBuilder().messageBody("message-" + i).build());
    }
    List<String> receiptHandles =
        client
            .receiveMessage(receiveMessageRequest.toBuilder().maxNumberOfMessages(count).build())
            .messages()
            .stream()
            .map(message -> message.receiptHandle())
            .collect(toList());
    assertThat(receiptHandles).hasSize(count);
    getTesting().clearData();
    return receiptHandles;
  }

  @Test
  void testBatchSqsProducerConsumerServicesSync() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());

    client.createQueue(createQueueRequest);
    client.sendMessageBatch(sendMessageBatchRequest);

    ReceiveMessageResponse response = client.receiveMessage(receiveMessageBatchRequest);
    // ids of the messages that carry a creation context, in the order they were received, which is
    // the order the receive span links them in. indexed access avoids the tracing iterator, which
    // would start the process spans too early
    List<String> propagatedMessageIds = new ArrayList<>();
    for (int i = 0; i < response.messages().size(); i++) {
      Message message = response.messages().get(i);
      if (message.attributesAsStrings().containsKey("AWSTraceHeader")
          || message.messageAttributes().containsKey("traceparent")) {
        propagatedMessageIds.add(message.messageId());
      }
    }
    response.messages().forEach(message -> getTesting().runWithSpan("process child", () -> {}));

    int totalAttrs =
        response.messages().stream().mapToInt(message -> message.messageAttributes().size()).sum();

    assertThat(response.messages()).hasSize(3);

    assertThat(totalAttrs).isEqualTo(10 + (isSqsAttributeInjectionEnabled() ? 3 : 0));

    if (emitStableMessagingSemconv() && canInjectBatchCreationContext()) {
      List<SpanData> createSpans = new ArrayList<>();
      List<Consumer<TraceAssert>> stableTraceAsserts = new ArrayList<>();
      stableTraceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)));
      for (int i = 0; i < 3; i++) {
        stableTraceAsserts.add(
            trace -> {
              SpanData createSpan = trace.getSpan(0);
              createSpans.add(createSpan);
              trace.hasSpansSatisfyingExactly(
                  AbstractAws2SqsTracingTest::assertCreateSpan,
                  span -> assertProcessSpan(span, createSpan, createSpan, false),
                  span ->
                      span.hasName("process child")
                          .hasParent(trace.getSpan(1))
                          .hasTotalAttributeCount(0));
            });
      }
      stableTraceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      publishSpan(span, queueUrl, "SendMessageBatch", 3L)
                          .hasLinksSatisfying(
                              links -> {
                                assertThat(links)
                                    .extracting(link -> link.getSpanContext().getSpanId())
                                    .containsExactlyInAnyOrder(
                                        createSpans.get(0).getSpanId(),
                                        createSpans.get(1).getSpanId(),
                                        createSpans.get(2).getSpanId());
                                assertThat(links)
                                    .extracting(LinkData::getAttributes)
                                    .containsOnly(Attributes.empty());
                              })));
      stableTraceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("receive testSdkSqs")
                          .hasKind(SpanKind.CLIENT)
                          .hasNoParent()
                          .hasAttributesSatisfyingExactly(
                              equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                              equalTo(RPC_SYSTEM, "aws-api"),
                              equalTo(RPC_SERVICE, "Sqs"),
                              equalTo(RPC_METHOD, "ReceiveMessage"),
                              equalTo(HTTP_REQUEST_METHOD, "POST"),
                              equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                              satisfies(
                                  URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                              equalTo(SERVER_ADDRESS, "localhost"),
                              equalTo(SERVER_PORT, sqsPort),
                              equalTo(MESSAGING_SYSTEM, AWS_SQS),
                              equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                              equalTo(MESSAGING_OPERATION_NAME, "receive"),
                              equalTo(
                                  MESSAGING_OPERATION,
                                  emitOldMessagingSemconv() ? "receive" : null),
                              equalTo(MESSAGING_OPERATION_TYPE, "receive"),
                              equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3))
                          .hasLinksSatisfying(
                              links ->
                                  assertThat(links)
                                      .extracting(link -> link.getSpanContext().getSpanId())
                                      .containsExactlyInAnyOrder(
                                          createSpans.get(0).getSpanId(),
                                          createSpans.get(1).getSpanId(),
                                          createSpans.get(2).getSpanId()))));
      getTesting().waitAndAssertTraces(stableTraceAsserts);
      return;
    }

    int propagatedMessages = 3;
    assertThat(propagatedMessageIds).hasSize(propagatedMessages);
    // without an ambient span the process spans are parented to the message creation context, which
    // puts them in the same trace as the publish span
    boolean processInPublishTrace = emitStableMessagingSemconv();

    AtomicReference<SpanData> publishSpan = new AtomicReference<>();

    List<Consumer<TraceAssert>> traceAsserts = new ArrayList<>();
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("Sqs.CreateQueue").hasKind(SpanKind.CLIENT)));
    traceAsserts.add(
        trace -> {
          publishSpan.set(trace.getSpan(0));

          List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
          spanAsserts.add(span -> publishSpan(span, queueUrl, "SendMessageBatch", 3L));

          if (processInPublishTrace) {
            for (int i = 0; i < propagatedMessages; i++) {
              int finalI = i;
              spanAsserts.add(
                  span -> assertProcessSpan(span, trace.getSpan(0), publishSpan.get(), false));
              spanAsserts.add(
                  span ->
                      span.hasName("process child")
                          .hasParent(trace.getSpan(1 + 2 * finalI))
                          .hasTotalAttributeCount(0));
            }
          }

          trace.hasSpansSatisfyingExactlyInAnyOrder(spanAsserts);
        });
    traceAsserts.add(
        trace -> {
          List<Consumer<SpanDataAssert>> spanAsserts = new ArrayList<>();
          spanAsserts.add(
              span -> {
                span.hasName(
                        emitStableMessagingSemconv() ? "receive testSdkSqs" : "testSdkSqs receive")
                    .hasKind(emitStableMessagingSemconv() ? SpanKind.CLIENT : SpanKind.CONSUMER)
                    .hasNoParent()
                    .hasAttributesSatisfyingExactly(
                        equalTo(stringKey("aws.agent"), "java-aws-sdk"),
                        equalTo(RPC_SYSTEM, "aws-api"),
                        equalTo(RPC_SERVICE, "Sqs"),
                        equalTo(RPC_METHOD, "ReceiveMessage"),
                        equalTo(HTTP_REQUEST_METHOD, "POST"),
                        equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                        satisfies(URL_FULL, val -> val.startsWith("http://localhost:" + sqsPort)),
                        equalTo(SERVER_ADDRESS, "localhost"),
                        equalTo(SERVER_PORT, sqsPort),
                        equalTo(MESSAGING_SYSTEM, AWS_SQS),
                        equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                        equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "receive" : null),
                        equalTo(
                            MESSAGING_OPERATION_NAME,
                            emitStableMessagingSemconv() ? "receive" : null),
                        equalTo(
                            MESSAGING_OPERATION_TYPE,
                            emitStableMessagingSemconv() ? "receive" : null),
                        equalTo(MESSAGING_BATCH_MESSAGE_COUNT, 3));

                if (emitStableMessagingSemconv()) {
                  // one link per message whose creation context could be propagated
                  span.hasLinksSatisfying(
                      links -> {
                        assertThat(links).hasSize(propagatedMessages);
                        for (int i = 0; i < links.size(); i++) {
                          assertMessageLink(
                              links.get(i), publishSpan.get(), propagatedMessageIds.get(i));
                        }
                      });
                } else {
                  span.hasTotalRecordedLinks(0);
                }
              });

          if (!processInPublishTrace) {
            // one of the 3 process spans is expected to not have a span link
            for (int i = 0; i <= 2; i++) {
              int finalI = i;
              spanAsserts.add(
                  span ->
                      assertProcessSpan(
                          span,
                          trace.getSpan(0),
                          finalI < propagatedMessages ? publishSpan.get() : null,
                          false));
              spanAsserts.add(
                  span ->
                      span.hasName("process child")
                          .hasParent(trace.getSpan(1 + 2 * finalI))
                          .hasTotalAttributeCount(0));
            }
          }

          trace.hasSpansSatisfyingExactlyInAnyOrder(spanAsserts);
        });

    if (processInPublishTrace && propagatedMessages < 3) {
      // the message that did not carry a creation context starts a trace of its own
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> assertProcessSpan(span, null, null, false),
                  span ->
                      span.hasName("process child")
                          .hasParent(trace.getSpan(0))
                          .hasTotalAttributeCount(0)));
    }

    getTesting().waitAndAssertTraces(traceAsserts);
  }

  @Test
  void testProducerMetrics() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    client.createQueue(createQueueRequest);
    getTesting().clearData();

    client.sendMessage(sendMessageRequest);
    client.sendMessageBatch(sendMessageBatchRequest);

    SqsMetricsAssertions.assertProducerMetrics(getTesting(), sqsPort, 2, 4);
  }

  @Test
  void testReceiveAndProcessMetrics() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    client.createQueue(createQueueRequest);
    client.sendMessageBatch(sendMessageBatchRequest);
    getTesting().clearData();

    ReceiveMessageResponse response =
        client.receiveMessage(
            receiveMessageBatchRequest.toBuilder().maxNumberOfMessages(10).build());
    response.messages().forEach(message -> {});
    ReceiveMessageResponse emptyResponse =
        client.receiveMessage(
            receiveMessageBatchRequest.toBuilder().maxNumberOfMessages(10).build());

    assertThat(response.messages()).hasSize(3);
    assertThat(emptyResponse.messages()).isEmpty();
    // the poll that returned no messages is not instrumented, so only one receive operation is
    // recorded
    SqsMetricsAssertions.assertReceiveAndProcessMetrics(getTesting(), sqsPort, 1, 3);
  }

  @Test
  void testSettleMetrics() {
    SqsClientBuilder builder = SqsClient.builder();
    configureSdkClient(builder);
    SqsClient client = configureSqsClient(builder.build());
    List<String> receiptHandles = createMessages(client, 2);
    getTesting().clearData();

    client.deleteMessage(
        DeleteMessageRequest.builder()
            .queueUrl(queueUrl)
            .receiptHandle(receiptHandles.get(0))
            .build());
    client.deleteMessageBatch(
        DeleteMessageBatchRequest.builder()
            .queueUrl(queueUrl)
            .entries(
                DeleteMessageBatchRequestEntry.builder()
                    .id("0")
                    .receiptHandle(receiptHandles.get(1))
                    .build())
            .build());

    SqsMetricsAssertions.assertSettleMetrics(getTesting(), sqsPort, 2);
  }

  private static void assertMessageLink(LinkData link, SpanData creationContext, String messageId) {
    assertThat(link.getSpanContext().getSpanId()).isEqualTo(creationContext.getSpanId());
    assertThat(link.getAttributes()).isEqualTo(Attributes.of(MESSAGING_MESSAGE_ID, messageId));
  }

  private static void assertBareLink(LinkData link, SpanData creationContext) {
    assertThat(link.getSpanContext().getSpanId()).isEqualTo(creationContext.getSpanId());
    assertThat(link.getAttributes()).isEqualTo(Attributes.empty());
  }
}
