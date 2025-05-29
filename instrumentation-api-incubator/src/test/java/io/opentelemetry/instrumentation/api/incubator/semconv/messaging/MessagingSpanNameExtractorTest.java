/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

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

  @Mock MessagingAttributesGetter<Message, Void> getter;

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
      when(getter.isTemporaryDestination(message)).thenReturn(true);
    } else {
      when(getter.getDestination(message)).thenReturn(destinationName);
    }

    SpanNameExtractor<Message> underTest = MessagingSpanNameExtractor.create(getter, operation);

    // when
    String spanName = underTest.extract(message);

    // then
    assertEquals(expectedSpanName, spanName);
  }

  static Stream<Arguments> spanNameParams() {
    return Stream.of(
        Arguments.of(false, "destination", MessageOperation.PUBLISH, "destination publish"),
        Arguments.of(true, null, MessageOperation.PROCESS, "(temporary) process"),
        Arguments.of(false, null, MessageOperation.RECEIVE, "unknown receive"));
  }

  static class Message {}
}
