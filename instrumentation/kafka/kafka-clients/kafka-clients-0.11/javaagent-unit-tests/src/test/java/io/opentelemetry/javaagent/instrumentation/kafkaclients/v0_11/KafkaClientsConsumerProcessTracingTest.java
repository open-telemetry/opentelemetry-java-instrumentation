/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import org.junit.jupiter.api.Test;

class KafkaClientsConsumerProcessTracingTest {

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
