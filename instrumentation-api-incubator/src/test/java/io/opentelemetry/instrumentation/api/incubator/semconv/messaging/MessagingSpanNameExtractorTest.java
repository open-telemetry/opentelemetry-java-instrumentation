/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
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
      boolean isAnonymousQueue,
      String destinationName,
      String destinationTemplate,
      MessagingOperationType operationType,
      String operationName,
      String oldSpanName,
      String spanName) {
    // given
    Message message = new Message();

    if (emitStableMessagingSemconv()) {
      when(getter.getDestinationTemplate(message)).thenReturn(destinationTemplate);
      if (destinationTemplate == null) {
        when(getter.isTemporaryDestination(message)).thenReturn(isTemporaryQueue);
        if (!isTemporaryQueue) {
          when(getter.isAnonymousDestination(message)).thenReturn(isAnonymousQueue);
          if (!isAnonymousQueue) {
            when(getter.getDestination(message)).thenReturn(destinationName);
          }
        }
      }
    } else {
      when(getter.isTemporaryDestination(message)).thenReturn(isTemporaryQueue);
      if (!isTemporaryQueue) {
        when(getter.getDestination(message)).thenReturn(destinationName);
      }
    }

    SpanNameExtractor<Message> underTest =
        MessagingSpanNameExtractor.builder(getter, operationType)
            .setOperationName(operationName)
            .build();

    // when
    String actualSpanName = underTest.extract(message);

    // then
    assertThat(actualSpanName).isEqualTo(emitStableMessagingSemconv() ? spanName : oldSpanName);
  }

  static Stream<Arguments> spanNameParams() {
    return Stream.of(
        argumentSet(
            "operation name override",
            false,
            false,
            "destination",
            null,
            MessagingOperationType.SEND,
            "send",
            "destination publish",
            "send destination"),
        argumentSet(
            "temporary destination",
            true,
            false,
            "generated",
            null,
            MessagingOperationType.PROCESS,
            "process",
            "(temporary) process",
            "process"),
        argumentSet(
            "missing destination",
            false,
            false,
            null,
            null,
            MessagingOperationType.RECEIVE,
            "receive",
            "unknown receive",
            "receive"),
        argumentSet(
            "destination template",
            false,
            false,
            "customer-42",
            "customer-{id}",
            MessagingOperationType.SEND,
            "send",
            "customer-42 publish",
            "send customer-{id}"),
        argumentSet(
            "anonymous destination",
            false,
            true,
            "generated",
            null,
            MessagingOperationType.PROCESS,
            "process",
            "generated process",
            "process"));
  }

  static class Message {}
}
