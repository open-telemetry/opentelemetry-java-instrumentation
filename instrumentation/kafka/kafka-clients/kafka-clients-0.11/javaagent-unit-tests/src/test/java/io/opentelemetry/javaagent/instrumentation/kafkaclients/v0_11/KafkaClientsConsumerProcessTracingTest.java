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
  void shouldRestoreProcessSpanSuppression() {
    assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
    assertThat(processSpanEnabledSupplier().getAsBoolean()).isTrue();

    Boolean previous = processSpanSuppression().set(Boolean.TRUE);
    try {
      assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
      assertThat(processSpanEnabledSupplier().getAsBoolean()).isFalse();
    } finally {
      processSpanSuppression().restore(previous);
    }

    assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
    assertThat(processSpanEnabledSupplier().getAsBoolean()).isTrue();
  }

  @Test
  void shouldRestoreNestedProcessSpanSuppression() {
    Boolean outerPrevious = processSpanSuppression().set(Boolean.TRUE);
    try {
      Boolean innerPrevious = processSpanSuppression().set(Boolean.TRUE);
      try {
        assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
      } finally {
        processSpanSuppression().restore(innerPrevious);
      }
      assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isTrue();
    } finally {
      processSpanSuppression().restore(outerPrevious);
    }

    assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = processSpanSuppression().set(Boolean.TRUE);
              try {
                throw new IllegalStateException("test");
              } finally {
                processSpanSuppression().restore(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(KafkaClientsConsumerProcessTracing.isProcessSpanSuppressed()).isFalse();
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
