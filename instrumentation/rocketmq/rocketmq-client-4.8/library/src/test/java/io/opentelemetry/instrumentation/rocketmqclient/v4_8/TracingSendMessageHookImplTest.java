/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.hook.SendMessageHook;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class TracingSendMessageHookImplTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @CsvSource({"true, false", "false, false", "true, true", "false, true"})
  void batchSend(boolean enabled, boolean failed) throws Exception {
    MessageBatch batch = batch();
    SendMessageContext request = request(batch);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(enabled)
            .build()
            .createSendMessageHook();
    RuntimeException error = failed ? new IllegalStateException("send failed") : null;

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          finish(request, hook, error);
        });

    boolean creates = enabled && emitStableMessagingSemconv();
    List<Message> decoded = decode(batch);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSize(creates ? 4 : 2);
          SpanData parent = trace.getSpan(0);
          assertThat(parent).hasName("parent");
          SpanData send = trace.getSpan(creates ? 3 : 1);
          assertThat(send)
              .hasName(emitStableMessagingSemconv() ? "send topic" : "topic publish")
              .hasKind(creates ? CLIENT : PRODUCER)
              .hasParent(parent)
              .hasStatus(failed ? StatusData.error() : StatusData.unset())
              .hasAttributesSatisfyingExactly(
                  equalTo(MESSAGING_SYSTEM, "rocketmq"),
                  equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                  equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "publish" : null),
                  equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? "send" : null),
                  equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? "send" : null),
                  equalTo(MESSAGING_BATCH_MESSAGE_COUNT, emitStableMessagingSemconv() ? 2L : null),
                  equalTo(MESSAGING_ROCKETMQ_NAMESPACE, emitStableMessagingSemconv() ? "" : null),
                  equalTo(
                      MESSAGING_MESSAGE_ID,
                      !emitStableMessagingSemconv() && !failed ? "result-id" : null),
                  equalTo(
                      ERROR_TYPE,
                      emitStableMessagingSemconv() && failed
                          ? IllegalStateException.class.getName()
                          : null));
          assertThat(send.getEndEpochNanos()).isGreaterThan(send.getStartEpochNanos());
          for (int i = 0; i < 2; i++) {
            SpanData creation = creates ? trace.getSpan(i + 1) : send;
            assertThat(extract(decoded.get(i))).isEqualTo(remote(creation.getSpanContext()));
            if (creates) {
              assertThat(creation)
                  .hasName("create topic")
                  .hasKind(PRODUCER)
                  .hasParent(parent)
                  .hasStatus(StatusData.unset())
                  .hasAttributesSatisfyingExactly(
                      equalTo(MESSAGING_SYSTEM, "rocketmq"),
                      equalTo(MESSAGING_DESTINATION_NAME, "topic"),
                      equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? "create" : null),
                      equalTo(MESSAGING_OPERATION_NAME, "create"),
                      equalTo(MESSAGING_OPERATION_TYPE, "create"),
                      equalTo(MESSAGING_ROCKETMQ_NAMESPACE, ""),
                      equalTo(MESSAGING_MESSAGE_ID, "message-" + i));
              assertThat(creation.getEndEpochNanos()).isEqualTo(creation.getStartEpochNanos());
              assertThat(creation.getEndEpochNanos())
                  .isLessThanOrEqualTo(send.getStartEpochNanos());
            }
          }
          if (creates) {
            assertThat(send)
                .hasLinks(
                    LinkData.create(trace.getSpan(1).getSpanContext()),
                    LinkData.create(trace.getSpan(2).getSpanContext()));
          } else {
            assertThat(send).hasTotalRecordedLinks(0);
          }
        });
  }

  @Test
  void createsBatchMessageSpansByDefault() {
    SendMessageContext request = request(batch());
    SendMessageHook hook =
        RocketMqTelemetry.create(testing.getOpenTelemetry()).createSendMessageHook();

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          finish(request, hook, null);
        });

    testing.waitAndAssertTraces(trace -> trace.hasSize(emitStableMessagingSemconv() ? 4 : 2));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void preservesExistingCreationContext(boolean enabled) throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    MessageBatch batch = batch();
    Message first = batch.iterator().next();
    first.putUserProperty("traceparent", "00-00000000000000000000000000000001-0000000000000001-01");
    SpanContext existing = extract(first);
    batch.setBody(batch.encode());
    SendMessageContext request = request(batch);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(enabled)
            .build()
            .createSendMessageHook();

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          finish(request, hook, null);
        });

    List<Message> decoded = decode(batch);
    assertThat(extract(decoded.get(0))).isEqualTo(existing);
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSize(enabled ? 3 : 2);
          SpanData send = trace.getSpan(enabled ? 2 : 1);
          assertThat(send).hasKind(enabled ? CLIENT : PRODUCER);
          if (enabled) {
            assertThat(send)
                .hasLinks(
                    LinkData.create(existing), LinkData.create(trace.getSpan(1).getSpanContext()));
          } else {
            assertThat(send).hasLinks(LinkData.create(existing));
          }
          assertThat(extract(decoded.get(1))).isEqualTo(remote(trace.getSpan(1).getSpanContext()));
        });
  }

  @Test
  void usesClientKindWhenAllMessagesHaveExistingContextsAndCreationIsDisabled() throws Exception {
    assumeTrue(emitStableMessagingSemconv());
    MessageBatch batch = batch();
    List<SpanContext> existing = new ArrayList<>();
    int id = 1;
    for (Message message : batch) {
      message.putUserProperty(
          "traceparent", "00-00000000000000000000000000000001-000000000000000" + id++ + "-01");
      existing.add(extract(message));
    }
    batch.setBody(batch.encode());
    SendMessageContext request = request(batch);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(false)
            .build()
            .createSendMessageHook();

    hook.sendMessageBefore(request);
    finish(request, hook, null);

    assertThat(decode(batch))
        .extracting(TracingSendMessageHookImplTest::extract)
        .containsExactlyElementsOf(existing);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("send topic")
                        .hasKind(CLIENT)
                        .hasNoParent()
                        .hasLinks(
                            LinkData.create(existing.get(0)), LinkData.create(existing.get(1)))));
  }

  @ParameterizedTest
  @CsvSource({"true, false", "false, false", "true, true", "false, true"})
  void singleSendIsUnaffected(boolean enabled, boolean failed) {
    Message message = new Message("topic", new byte[] {1});
    SendMessageContext request = request(message);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(enabled)
            .build()
            .createSendMessageHook();

    hook.sendMessageBefore(request);
    finish(request, hook, failed ? new IllegalStateException("send failed") : null);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableMessagingSemconv() ? "send topic" : "topic publish")
                        .hasKind(PRODUCER)
                        .hasStatus(failed ? StatusData.error() : StatusData.unset())
                        .hasTotalRecordedLinks(0)
                        .satisfies(
                            spanData ->
                                assertThat(extract(message))
                                    .isEqualTo(remote(spanData.getSpanContext())))));
  }

  @Test
  void asynchronousSendWaitsForCompletion() {
    SendMessageContext request = request(batch());
    request.setCommunicationMode(CommunicationMode.ASYNC);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(false)
            .build()
            .createSendMessageHook();

    hook.sendMessageBefore(request);
    hook.sendMessageAfter(request);
    assertThat(testing.spans()).isEmpty();
    finish(request, hook, null);

    testing.waitAndAssertTraces(trace -> trace.hasSize(1));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void oneWaySendFinishesWithoutResult(boolean enabled) {
    SendMessageContext request = request(batch());
    request.setCommunicationMode(CommunicationMode.ONEWAY);
    SendMessageHook hook =
        RocketMqTelemetry.builder(testing.getOpenTelemetry())
            .setBatchSendMessageCreationSpansEnabled(enabled)
            .build()
            .createSendMessageHook();

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          hook.sendMessageAfter(request);
        });

    testing.waitAndAssertTraces(
        trace -> trace.hasSize(enabled && emitStableMessagingSemconv() ? 4 : 2));
  }

  @Test
  void linksCreationSpansWithoutPropagation() {
    assumeTrue(emitStableMessagingSemconv());
    OpenTelemetry openTelemetry =
        new OpenTelemetry() {
          @Override
          public TracerProvider getTracerProvider() {
            return testing.getOpenTelemetry().getTracerProvider();
          }

          @Override
          public ContextPropagators getPropagators() {
            return ContextPropagators.noop();
          }
        };
    SendMessageHook hook = RocketMqTelemetry.create(openTelemetry).createSendMessageHook();
    SendMessageContext request = request(batch());

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          finish(request, hook, null);
        });

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSize(4);
          assertThat(trace.getSpan(3))
              .hasKind(CLIENT)
              .hasLinks(
                  LinkData.create(trace.getSpan(1).getSpanContext()),
                  LinkData.create(trace.getSpan(2).getSpanContext()));
        });
  }

  @Test
  void creationSpansUseProducerNamespace() {
    assumeTrue(emitStableMessagingSemconv());
    MessageBatch batch = batch();
    batch.setTopic("namespace%topic");
    for (Message message : batch) {
      message.setTopic("namespace%topic");
    }
    SendMessageContext request = request(batch);
    request.setNamespace("namespace");
    SendMessageHook hook =
        RocketMqTelemetry.create(testing.getOpenTelemetry()).createSendMessageHook();

    testing.runWithSpan(
        "parent",
        () -> {
          hook.sendMessageBefore(request);
          finish(request, hook, null);
        });

    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSize(4);
          for (int i = 1; i <= 2; i++) {
            assertThat(trace.getSpan(i))
                .hasName("create topic")
                .hasAttribute(MESSAGING_ROCKETMQ_NAMESPACE, "namespace");
          }
        });
  }

  private static MessageBatch batch() {
    Message first = new Message("topic", new byte[] {1});
    Message second = new Message("topic", new byte[] {2});
    MessageClientIDSetter.setUniqID(first);
    MessageClientIDSetter.setUniqID(second);
    first.getProperties().put(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX, "message-0");
    second.getProperties().put(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX, "message-1");
    MessageBatch batch = MessageBatch.generateFromList(asList(first, second));
    batch.setBody(batch.encode());
    return batch;
  }

  private static SendMessageContext request(Message message) {
    SendMessageContext request = new SendMessageContext();
    request.setMessage(message);
    request.setCommunicationMode(CommunicationMode.SYNC);
    return request;
  }

  private static void finish(
      SendMessageContext request, SendMessageHook hook, RuntimeException error) {
    if (error != null) {
      request.setException(error);
    } else {
      SendResult result = new SendResult();
      result.setSendStatus(SendStatus.SEND_OK);
      result.setMsgId("result-id");
      request.setSendResult(result);
    }
    hook.sendMessageAfter(request);
  }

  private static List<Message> decode(MessageBatch batch) throws Exception {
    List<Message> messages = MessageDecoder.decodeMessages(ByteBuffer.wrap(batch.getBody()));
    // The broker appends envelope properties after the serialized per-message properties.
    for (Message message : messages) {
      message.getProperties().putAll(batch.getProperties());
    }
    return messages;
  }

  private static SpanContext extract(Message message) {
    return Span.fromContext(
            W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), message, new MessageExtractAdapter()))
        .getSpanContext();
  }

  private static SpanContext remote(SpanContext context) {
    return SpanContext.createFromRemoteParent(
        context.getTraceId(),
        context.getSpanId(),
        context.getTraceFlags(),
        context.getTraceState());
  }
}
