/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaPropagation;
import org.junit.jupiter.api.Test;

class KafkaPropagationTest {

  @Test
  void detectsSpanContextPropagation() {
    assertThat(KafkaPropagation.propagatesSpanContext(W3CTraceContextPropagator.getInstance()))
        .isTrue();
    assertThat(KafkaPropagation.propagatesSpanContext(TextMapPropagator.noop())).isFalse();
    assertThat(KafkaPropagation.propagatesSpanContext(W3CBaggagePropagator.getInstance()))
        .isFalse();
  }
}
