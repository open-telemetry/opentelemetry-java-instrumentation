/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11.StateHolder.holder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class StateHolderTest {

  @Test
  void nestedProcessRestoresOuterHolderAfterFailure() {
    StateHolder outer = StreamTaskInstrumentation.ProcessAdvice.onEnter();
    try {
      StateHolder inner = StreamTaskInstrumentation.ProcessAdvice.onEnter();
      assertThat(holder().get()).isSameAs(inner);
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(inner, new IllegalStateException());
      assertThat(holder().get()).isSameAs(outer);
    } finally {
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(outer, null);
    }
    assertThat(holder().get()).isNull();
  }

  @Test
  void nestedProcessPreservesNullMask() {
    StateHolder outer = StreamTaskInstrumentation.ProcessAdvice.onEnter();
    try {
      StateHolder masked = holder().set(null);
      try {
        StateHolder inner = StreamTaskInstrumentation.ProcessAdvice.onEnter();
        StreamTaskInstrumentation.ProcessAdvice.stopSpan(inner, null);
        assertThat(holder().get()).isNull();
      } finally {
        holder().restore(masked);
      }
      assertThat(holder().get()).isSameAs(outer);
    } finally {
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(outer, null);
    }
    assertThat(holder().get()).isNull();
  }

  @Test
  void failedEntryDoesNotClearOuterHolder() {
    StateHolder outer = StreamTaskInstrumentation.ProcessAdvice.onEnter();
    try {
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(null, new IllegalStateException());
      assertThat(holder().get()).isSameAs(outer);
    } finally {
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(outer, null);
    }
    assertThat(holder().get()).isNull();
  }

  @Test
  void restoresOuterHolderBeforeClosingScope() {
    StateHolder outer = StreamTaskInstrumentation.ProcessAdvice.onEnter();
    try {
      StateHolder inner = StreamTaskInstrumentation.ProcessAdvice.onEnter();
      KafkaProcessRequest request =
          KafkaProcessRequest.create(new ConsumerRecord<>("topic", 0, 0, "key", "value"), null);
      inner.set(
          request,
          Context.root(),
          () -> {
            assertThat(holder().get()).isSameAs(outer);
            throw new IllegalStateException("scope close");
          });
      assertThatThrownBy(() -> StreamTaskInstrumentation.ProcessAdvice.stopSpan(inner, null))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("scope close");
      assertThat(holder().get()).isSameAs(outer);
    } finally {
      StreamTaskInstrumentation.ProcessAdvice.stopSpan(outer, null);
    }
    assertThat(holder().get()).isNull();
  }
}
