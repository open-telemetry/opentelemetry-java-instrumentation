/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.currentProcessSpanSuppression;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import org.junit.jupiter.api.Test;

class KafkaClientsConsumerProcessTracingTest {

  @Test
  void shouldRestoreProcessSpanSuppression() {
    Boolean previous = currentProcessSpanSuppression().set(Boolean.TRUE);
    try {
      assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isFalse();
    } finally {
      currentProcessSpanSuppression().restore(previous);
    }

    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }

  @Test
  void shouldRestoreNestedProcessSpanSuppression() {
    Boolean outerPrevious = currentProcessSpanSuppression().set(Boolean.TRUE);
    try {
      Boolean innerPrevious = currentProcessSpanSuppression().set(Boolean.TRUE);
      try {
        assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isFalse();
      } finally {
        currentProcessSpanSuppression().restore(innerPrevious);
      }
      assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isFalse();
    } finally {
      currentProcessSpanSuppression().restore(outerPrevious);
    }

    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isTrue();
  }

  @Test
  void shouldCleanUpAfterException() {
    assertThatThrownBy(
            () -> {
              Boolean previous = currentProcessSpanSuppression().set(Boolean.TRUE);
              try {
                throw new IllegalStateException("test");
              } finally {
                currentProcessSpanSuppression().restore(previous);
              }
            })
        .isInstanceOf(IllegalStateException.class);

    assertThat(KafkaClientsConsumerProcessTracing.isWrappingEnabled()).isTrue();
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
