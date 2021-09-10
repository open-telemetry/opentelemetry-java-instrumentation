/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingSpanNameExtractorTest {
  @Mock MessagingAttributesExtractor<Message, Void> attributesExtractor;

  @ParameterizedTest
  @MethodSource("spanNameParams")
  void shouldExtractSpanName(
      boolean isTemporaryQueue,
      String destinationName,
      MessageOperation operation,
      String expectedSpanName) {
    // given
    Message message = new Message();

    if (isTemporaryQueue) {
      given(attributesExtractor.temporaryDestination(message)).willReturn(true);
    } else {
      given(attributesExtractor.destination(message)).willReturn(destinationName);
    }
    given(attributesExtractor.operation()).willReturn(operation);

    SpanNameExtractor<Message> underTest = MessagingSpanNameExtractor.create(attributesExtractor);

    // when
    String spanName = underTest.extract(message);

    // then
    assertEquals(expectedSpanName, spanName);
  }

  static Stream<Arguments> spanNameParams() {
    return Stream.of(
        Arguments.of(false, "destination", MessageOperation.SEND, "destination send"),
        Arguments.of(true, null, MessageOperation.PROCESS, "(temporary) process"),
        Arguments.of(false, null, MessageOperation.RECEIVE, "unknown receive"));
  }

  static class Message {}
}
