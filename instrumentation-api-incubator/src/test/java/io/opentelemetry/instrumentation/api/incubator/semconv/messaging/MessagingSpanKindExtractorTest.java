/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingSpanKindExtractorTest {

  @ParameterizedTest
  @MethodSource("spanKinds")
  void extractsSpanKind(
      MessagingOperationType operationType,
      boolean isSpanContextPropagated,
      SpanKind oldKind,
      SpanKind kind) {
    SpanKind actualKind =
        MessagingSpanKindExtractor.create(operationType, isSpanContextPropagated)
            .extract(new Object());

    assertThat(actualKind).isEqualTo(emitStableMessagingSemconv() ? kind : oldKind);
  }

  @Test
  void sendDefaultsToPropagatedSpanContext() {
    SpanKind spanKind =
        MessagingSpanKindExtractor.create(MessagingOperationType.SEND).extract(new Object());

    assertThat(spanKind).isEqualTo(SpanKind.PRODUCER);
  }

  @SuppressWarnings("OtelDeprecatedApiUsage")
  @Test
  void messageOperationUsesLegacySpanKind() {
    SpanKind receiveKind =
        MessagingSpanKindExtractor.create(MessageOperation.RECEIVE).extract(new Object());

    assertThat(receiveKind).isEqualTo(SpanKind.CONSUMER);
  }

  private static Stream<Arguments> spanKinds() {
    return Stream.of(
        argumentSet(
            "create", MessagingOperationType.CREATE, true, SpanKind.PRODUCER, SpanKind.PRODUCER),
        argumentSet(
            "send with propagated context",
            MessagingOperationType.SEND,
            true,
            SpanKind.PRODUCER,
            SpanKind.PRODUCER),
        argumentSet(
            "send without propagated context",
            MessagingOperationType.SEND,
            false,
            SpanKind.PRODUCER,
            SpanKind.CLIENT),
        argumentSet(
            "receive", MessagingOperationType.RECEIVE, true, SpanKind.CONSUMER, SpanKind.CLIENT),
        argumentSet(
            "process", MessagingOperationType.PROCESS, true, SpanKind.CONSUMER, SpanKind.CONSUMER),
        argumentSet(
            "settle", MessagingOperationType.SETTLE, true, SpanKind.CLIENT, SpanKind.CLIENT));
  }
}
