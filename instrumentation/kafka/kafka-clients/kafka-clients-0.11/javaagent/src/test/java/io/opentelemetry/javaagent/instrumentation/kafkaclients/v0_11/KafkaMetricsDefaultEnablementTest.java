/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaClientBaseTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaMetricsDefaultEnablementTest extends KafkaClientBaseTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";
  private static final boolean METRICS_ENABLED =
      Boolean.getBoolean("otel.instrumentation.kafka-clients-metrics.enabled");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void defaultEnablement() throws Exception {
    testing.runWithSpan(
        "parent",
        () -> producer.send(new ProducerRecord<>(SHARED_TOPIC, 10, "hello")).get(5, SECONDS));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span -> span.hasName("send " + SHARED_TOPIC).hasParent(trace.getSpan(0))));

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics -> metrics.isNotEmpty());

    if (METRICS_ENABLED) {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME, "kafka.producer.record_send_rate", metrics -> metrics.isNotEmpty());
    } else {
      assertThat(testing.metrics())
          .noneMatch(
              metric ->
                  metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                      && metric.getName().startsWith("kafka."));
    }
  }
}
