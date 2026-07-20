/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_CONVERSATION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ENVELOPE_SIZE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingAttributesExtractorTest {

  @SuppressWarnings("deprecation") // using deprecated semconv
  @ParameterizedTest
  @MethodSource("destinations")
  void shouldExtractAllAvailableAttributes(
      boolean temporary,
      boolean anonymous,
      String destination,
      MessagingOperationType operationType,
      String operationName) {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("system", "myQueue");
    request.put("destinationKind", "topic");
    request.put("destination", destination);
    request.put("destinationTemplate", destination);
    if (temporary) {
      request.put("temporaryDestination", "y");
    }
    if (anonymous) {
      request.put("anonymousDestination", "y");
    }
    request.put("url", "http://broker/topic");
    request.put("conversationId", "42");
    request.put("messageId", "42");
    request.put("bodySize", "100");
    request.put("envelopeSize", "120");
    request.put("clientId", "43");
    request.put("batchMessageCount", "2");

    AttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.builderForOperationType(TestGetter.INSTANCE, operationType)
            .setOperationName(operationName)
            .build();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, "42", new IllegalStateException());

    // then
    List<MapEntry<AttributeKey<?>, Object>> expectedEntries = new ArrayList<>();
    expectedEntries.add(entry(MESSAGING_SYSTEM, "myQueue"));
    if (temporary) {
      expectedEntries.add(entry(MESSAGING_DESTINATION_TEMPORARY, true));
      if (emitStableMessagingSemconv()) {
        expectedEntries.add(entry(MESSAGING_DESTINATION_NAME, destination));
        expectedEntries.add(entry(MESSAGING_DESTINATION_TEMPLATE, destination));
      } else {
        expectedEntries.add(entry(MESSAGING_DESTINATION_NAME, "(temporary)"));
      }
    } else {
      expectedEntries.add(entry(MESSAGING_DESTINATION_NAME, destination));
      expectedEntries.add(entry(MESSAGING_DESTINATION_TEMPLATE, destination));
    }
    if (anonymous) {
      expectedEntries.add(entry(MESSAGING_DESTINATION_ANONYMOUS, true));
    }
    expectedEntries.add(entry(MESSAGING_MESSAGE_CONVERSATION_ID, "42"));
    expectedEntries.add(entry(MESSAGING_MESSAGE_BODY_SIZE, 100L));
    expectedEntries.add(entry(MESSAGING_MESSAGE_ENVELOPE_SIZE, 120L));
    if (emitOldMessagingSemconv()) {
      expectedEntries.add(entry(stringKey("messaging.client_id"), "43"));
      expectedEntries.add(entry(MESSAGING_OPERATION, operationType.defaultOperationName()));
    }
    if (emitStableMessagingSemconv()) {
      expectedEntries.add(entry(stringKey("messaging.client.id"), "43"));
      expectedEntries.add(entry(MESSAGING_OPERATION_NAME, operationName));
      expectedEntries.add(entry(MESSAGING_OPERATION_TYPE, operationType.value()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    MapEntry<? extends AttributeKey<?>, ?>[] expectedEntriesArr =
        expectedEntries.toArray(new MapEntry[0]);
    assertThat(startAttributes.build()).containsOnly(expectedEntriesArr);

    if (emitStableMessagingSemconv()) {
      assertThat(endAttributes.build())
          .containsOnly(
              entry(MESSAGING_MESSAGE_ID, "42"),
              entry(MESSAGING_BATCH_MESSAGE_COUNT, 2L),
              entry(ERROR_TYPE, IllegalStateException.class.getName()));
    } else {
      assertThat(endAttributes.build())
          .containsOnly(
              entry(MESSAGING_MESSAGE_ID, "42"), entry(MESSAGING_BATCH_MESSAGE_COUNT, 2L));
    }
  }

  static Stream<Arguments> destinations() {
    return Stream.of(
        argumentSet(
            "create operation",
            false,
            false,
            "destination",
            MessagingOperationType.CREATE,
            "create"),
        argumentSet(
            "regular destination",
            false,
            false,
            "destination",
            MessagingOperationType.RECEIVE,
            "poll"),
        argumentSet(
            "temporary anonymous destination",
            true,
            true,
            "generated-destination",
            MessagingOperationType.PROCESS,
            "process"),
        argumentSet(
            "settle operation",
            false,
            false,
            "destination",
            MessagingOperationType.SETTLE,
            "settle"));
  }

  @ParameterizedTest
  @MethodSource("spanKeys")
  void shouldReturnSpanKey(MessagingOperationType operationType, SpanKey spanKey) {
    MessagingAttributesExtractor<Map<String, String>, String> underTest =
        new MessagingAttributesExtractor<>(
            TestGetter.INSTANCE,
            operationType,
            operationType.defaultOperationName(),
            true,
            new ArrayList<>());

    assertThat(underTest.internalGetSpanKey()).isSameAs(spanKey);
  }

  static Stream<Arguments> spanKeys() {
    return Stream.of(
        argumentSet("create", MessagingOperationType.CREATE, SpanKey.PRODUCER_CREATE),
        argumentSet("send", MessagingOperationType.SEND, SpanKey.PRODUCER),
        argumentSet("receive", MessagingOperationType.RECEIVE, SpanKey.CONSUMER_RECEIVE),
        argumentSet("process", MessagingOperationType.PROCESS, SpanKey.CONSUMER_PROCESS),
        argumentSet("settle", MessagingOperationType.SETTLE, SpanKey.CONSUMER_SETTLE));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void shouldSupportDeprecatedMessageOperation() {
    AttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.create(TestGetter.INSTANCE, MessageOperation.PUBLISH);

    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), singletonMap("anonymousDestination", "y"));

    assertThat(attributes.build())
        .containsOnly(
            entry(MESSAGING_DESTINATION_ANONYMOUS, true), entry(MESSAGING_OPERATION, "publish"));
  }

  @SuppressWarnings("deprecation") // testing deprecated API
  @Test
  void shouldRejectOperationNameForDeprecatedMessageOperation() {
    assertThatThrownBy(
            () ->
                MessagingAttributesExtractor.builder(TestGetter.INSTANCE, MessageOperation.PUBLISH)
                    .setOperationName("send"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Operation name is not configurable for legacy builders");
  }

  @Test
  void shouldExtractOperationNameWithoutOperationType() {
    MessagingOperationType operationType = null;
    AttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.builderForOperationType(TestGetter.INSTANCE, operationType)
            .setOperationName("ack")
            .build();

    AttributesBuilder attributes = Attributes.builder();
    underTest.onStart(attributes, Context.root(), emptyMap());

    Attributes expected =
        emitStableMessagingSemconv()
            ? Attributes.of(MESSAGING_OPERATION_NAME, "ack")
            : Attributes.empty();
    assertThat(attributes.build()).isEqualTo(expected);
  }

  @Test
  void shouldRequireOperationNameForStableSemconv() {
    assertThatThrownBy(
            () ->
                MessagingAttributesExtractor.builderForOperationType(TestGetter.INSTANCE, null)
                    .build())
        .isInstanceOf(NullPointerException.class)
        .hasMessage("operationName");
  }

  @Test
  void shouldExtractErrorTypeFromResponse() {
    AttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.createForOperationType(
            TestGetter.INSTANCE, MessagingOperationType.RECEIVE);

    AttributesBuilder attributes = Attributes.builder();
    underTest.onEnd(attributes, Context.root(), emptyMap(), "failure", null);

    Attributes expected =
        emitStableMessagingSemconv() ? Attributes.of(ERROR_TYPE, "failure") : Attributes.empty();
    assertThat(attributes.build()).isEqualTo(expected);
  }

  @SuppressWarnings("OtelDeprecatedApiUsage")
  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    AttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.create(TestGetter.INSTANCE, null);
    AttributesExtractor<Map<String, String>, String> builtUnderTest =
        MessagingAttributesExtractor.builder(TestGetter.INSTANCE, null).build();

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, emptyMap());
    AttributesBuilder builtStartAttributes = Attributes.builder();
    builtUnderTest.onStart(builtStartAttributes, context, emptyMap());

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, emptyMap(), null, null);

    // then
    assertThat(startAttributes.build()).isEmpty();
    assertThat(builtStartAttributes.build()).isEmpty();
    assertThat(endAttributes.build()).isEmpty();
  }

  enum TestGetter implements MessagingAttributesGetter<Map<String, String>, String> {
    INSTANCE;

    @Override
    public String getSystem(Map<String, String> request) {
      return request.get("system");
    }

    @Override
    public String getDestination(Map<String, String> request) {
      return request.get("destination");
    }

    @Nullable
    @Override
    public String getDestinationTemplate(Map<String, String> request) {
      return request.get("destinationTemplate");
    }

    @Override
    public boolean isTemporaryDestination(Map<String, String> request) {
      return request.containsKey("temporaryDestination");
    }

    @Override
    public boolean isAnonymousDestination(Map<String, String> request) {
      return request.containsKey("anonymousDestination");
    }

    @Override
    public String getConversationId(Map<String, String> request) {
      return request.get("conversationId");
    }

    @Nullable
    @Override
    public Long getMessageBodySize(Map<String, String> request) {
      String payloadSize = request.get("bodySize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Nullable
    @Override
    public Long getMessageEnvelopeSize(Map<String, String> request) {
      String payloadSize = request.get("envelopeSize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    public String getMessageId(Map<String, String> request, String response) {
      return request.get("messageId");
    }

    @Nullable
    @Override
    public String getClientId(Map<String, String> request) {
      return request.get("clientId");
    }

    @Nullable
    @Override
    public Long getBatchMessageCount(Map<String, String> request, @Nullable String response) {
      String payloadSize = request.get("batchMessageCount");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    public String getErrorType(Map<String, String> request, String response, Throwable error) {
      return "failure".equals(response) ? response : null;
    }
  }
}
