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

    TestMessagingAttributesExtractor underTest = new TestMessagingAttributesExtractor(operation);

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, request);

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, request, "42", null);

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

    assertThat(startAttributes.build()).containsOnly(expectedEntries.toArray(new MapEntry[0]));

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
    TestMessagingAttributesExtractor underTest =
        new TestMessagingAttributesExtractor(MessageOperation.SEND);

    // when
    AttributesBuilder startAttributes = Attributes.builder();
    underTest.onStart(startAttributes, Collections.emptyMap());

    AttributesBuilder endAttributes = Attributes.builder();
    underTest.onEnd(endAttributes, Collections.emptyMap(), null, null);

    // then
    assertThat(startAttributes.build().isEmpty()).isTrue();

    assertThat(endAttributes.build().isEmpty()).isTrue();
  }

  static class TestMessagingAttributesExtractor
      extends MessagingAttributesExtractor<Map<String, String>, String> {

    private final MessageOperation operation;

    TestMessagingAttributesExtractor(MessageOperation operation) {
      this.operation = operation;
    }

    @Override
    public MessageOperation operation() {
      return operation;
    }

    @Override
    protected String system(Map<String, String> request) {
      return request.get("system");
    }

    @Override
    protected String destinationKind(Map<String, String> request) {
      return request.get("destinationKind");
    }

    @Override
    protected String destination(Map<String, String> request) {
      return request.get("destination");
    }

    @Override
    protected boolean temporaryDestination(Map<String, String> request) {
      return request.containsKey("temporaryDestination");
    }

    @Override
    protected String protocol(Map<String, String> request) {
      return request.get("protocol");
    }

    @Override
    protected String protocolVersion(Map<String, String> request) {
      return request.get("protocolVersion");
    }

    @Override
    protected String url(Map<String, String> request) {
      return request.get("url");
    }

    @Override
    protected String conversationId(Map<String, String> request) {
      return request.get("conversationId");
    }

    @Override
    protected Long messagePayloadSize(Map<String, String> request) {
      String payloadSize = request.get("payloadSize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    protected Long messagePayloadCompressedSize(Map<String, String> request) {
      String payloadSize = request.get("payloadCompressedSize");
      return payloadSize == null ? null : Long.valueOf(payloadSize);
    }

    @Override
    protected String messageId(Map<String, String> request, String response) {
      return response;
    }
  }
}
