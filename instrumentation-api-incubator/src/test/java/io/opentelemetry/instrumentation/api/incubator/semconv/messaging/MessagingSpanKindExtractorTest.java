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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MessagingSpanKindExtractorTest {

  @ParameterizedTest
  @MethodSource("spanKinds")
  void extractsSpanKind(MessageOperation operation, SpanKind oldKind, SpanKind kind) {
    SpanKind actualKind = MessagingSpanKindExtractor.create(operation).extract(new Object());

    assertThat(actualKind).isEqualTo(emitStableMessagingSemconv() ? kind : oldKind);
  }

  private static Stream<Arguments> spanKinds() {
    return Stream.of(
        argumentSet("publish", MessageOperation.PUBLISH, SpanKind.PRODUCER, SpanKind.PRODUCER),
        argumentSet("receive", MessageOperation.RECEIVE, SpanKind.CONSUMER, SpanKind.CLIENT),
        argumentSet("process", MessageOperation.PROCESS, SpanKind.CONSUMER, SpanKind.CONSUMER));
  }
}
