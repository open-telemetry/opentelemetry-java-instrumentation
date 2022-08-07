/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.data.MapEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // operationName
class MessagingAttributesExtractorTest {

  @ParameterizedTest
  @MethodSource("destinations")
  void shouldExtractAllAvailableAttributes(
      boolean temporary,
      String destination,
      MessageOperation operation,
      String expectedDestination) {
    // given
    Map<String, String> request = new HashMap<>();
    request.put("system", "myQueue");
    request.put("destinationKind", "topic");
    request.put("destination", destination);
    if (temporary) {
      request.put("temporaryDestination", "y");
    }
    request.put("protocol", "AMQP");
    request.put("protocolVersion", "1.0.0");
    request.put("url", "http://broker/topic");
    request.put("conversationId", "42");
    request.put("payloadSize", "100");
    request.put("payloadCompressedSize", "10");

    MessagingAttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.create(TestGetter.INSTANCE, operation);

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, request, "42", null);

    // then
    List<MapEntry<AttributeKey<?>, Object>> expectedEntries = new ArrayList<>();
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_SYSTEM, "myQueue"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_DESTINATION_KIND, "topic"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_DESTINATION, expectedDestination));
    if (temporary) {
      expectedEntries.add(entry(SemanticAttributes.MESSAGING_TEMP_DESTINATION, true));
    }
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_PROTOCOL, "AMQP"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_PROTOCOL_VERSION, "1.0.0"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_URL, "http://broker/topic"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_CONVERSATION_ID, "42"));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, 100L));
    expectedEntries.add(
        entry(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES, 10L));
    expectedEntries.add(entry(SemanticAttributes.MESSAGING_OPERATION, operation.operationName()));

    @SuppressWarnings({"unchecked", "rawtypes"})
    MapEntry<? extends AttributeKey<?>, ?>[] expectedEntriesArr =
        expectedEntries.toArray(new MapEntry[0]);
    assertThat(startAttributes.build()).containsOnly(expectedEntriesArr);

    assertThat(endAttributes.build())
        .containsOnly(entry(SemanticAttributes.MESSAGING_MESSAGE_ID, "42"));
  }

  static Stream<Arguments> destinations() {
    return Stream.of(
        Arguments.of(false, "destination", MessageOperation.RECEIVE, "destination"),
        Arguments.of(true, null, MessageOperation.PROCESS, "(temporary)"));
  }

  @Test
  void shouldExtractNoAttributesIfNoneAreAvailable() {
    // given
    MessagingAttributesExtractor<Map<String, String>, String> underTest =
        MessagingAttributesExtractor.create(TestGetter.INSTANCE, MessageOperation.SEND);

    Context context = Context.root();

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, context, Collections.emptyMap());

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, context, Collections.emptyMap(), null, null);

    // then
    assertThat(startAttributes.build().isEmpty()).isTrue();

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  enum TestGetter implements MessagingAttributesGetter<Map<String, String>, String> {
    INSTANCE;

    @Override
    public String system(Map<String, String> request) {
      return request.get("system");
    }

    @Override
    public String destinationKind(Map<String, String> request) {
      return request.get("destinationKind");
    }

    @Override
    public String destination(Map<String, String> request) {
      return request.get("destination");
    }

    @Override
    public boolean temporaryDestination(Map<String, String> request) {
      return request.containsKey("temporaryDestination");
    }

    @Override
    public String protocol(Map<String, String> request) {
      return request.get("protocol");
    }

    @Override
    public String protocolVersion(Map<String, String> request) {
      return request.get("protocolVersion");
    }

    @Override
    public String url(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    public String conversationId(Map<String, String> request) {
      return request.get("conversationId");
    }

    @Override
    public Long messagePayloadSize(Map<String, String> request) {
      String payloadSize = request.get("payloadSize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    public Long messagePayloadCompressedSize(Map<String, String> request) {
      String payloadSize = request.get("payloadCompressedSize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    public String messageId(Map<String, String> request, String response) {
      return response;
    }
  }
}
