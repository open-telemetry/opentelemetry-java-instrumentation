/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanEnabledSupplier;
import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.processSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import org.junit.jupiter.api.Test;

class KafkaClientsConsumerProcessTracingTest {

  @Test
  void shouldScopeProcessSpanSuppressionOwnership() {
    assertThat(processSpanSuppression().isActive()).isFalse();
    assertThat(processSpanEnabledSupplier().getAsBoolean()).isTrue();

    boolean suppressionAcquired = processSpanSuppression().tryAcquire();
    try {
      assertThat(suppressionAcquired).isTrue();
      assertThat(processSpanSuppression().isActive()).isTrue();
      assertThat(processSpanEnabledSupplier().getAsBoolean()).isFalse();
    } finally {
      if (suppressionAcquired) {
        processSpanSuppression().release();
      }
    }

    assertThat(processSpanSuppression().isActive()).isFalse();
    assertThat(processSpanEnabledSupplier().getAsBoolean()).isTrue();
  }

  @Test
  void shouldPreserveNestedProcessSpanSuppression() {
    boolean outerSuppressionAcquired = processSpanSuppression().tryAcquire();
    try {
      assertThat(outerSuppressionAcquired).isTrue();

      boolean innerSuppressionAcquired = processSpanSuppression().tryAcquire();
      try {
        assertThat(innerSuppressionAcquired).isFalse();
        assertThat(processSpanSuppression().isActive()).isTrue();
      } finally {
        if (innerSuppressionAcquired) {
          processSpanSuppression().release();
        }
      }
      assertThat(processSpanSuppression().isActive()).isTrue();
    } finally {
      if (outerSuppressionAcquired) {
        processSpanSuppression().release();
      }
    }

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              boolean suppressionAcquired = processSpanSuppression().tryAcquire();
              try {
                assertThat(suppressionAcquired).isTrue();
                throw new IllegalStateException("test");
              } finally {
                if (suppressionAcquired) {
                  processSpanSuppression().release();
                }
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(processSpanSuppression().isActive()).isFalse();
  }

  @Test
  void shouldPreserveGlobalInstrumentationSuppression() {
    Context[] contexts = new Context[1];
    InstrumentationUtil.suppressInstrumentation(
        () ->
            contexts[0] =
                KafkaClientsConsumerProcessTracing.markFrameworkProcess(Context.current()));

    Context context =
        KafkaClientsConsumerProcessTracing.withoutFrameworkProcessSuppression(contexts[0]);

    assertThat(InstrumentationUtil.shouldSuppressInstrumentation(context)).isTrue();
  }
}
