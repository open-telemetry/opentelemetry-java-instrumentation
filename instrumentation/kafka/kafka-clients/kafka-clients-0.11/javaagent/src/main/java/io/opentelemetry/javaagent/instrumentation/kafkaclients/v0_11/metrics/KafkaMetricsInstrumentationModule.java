/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.metrics;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaMetricsInstrumentationModule extends InstrumentationModule {
  public KafkaMetricsInstrumentationModule() {
    super(
        "kafka-clients-metrics",
        "kafka-clients",
        "kafka-clients-metrics-0.11",
        "kafka-clients-0.11",
        "kafka");
  }

  @Override
  public boolean isIndyModule() {
    // OpenTelemetryMetricsReporter is not available in app class loader
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KafkaMetricsProducerInstrumentation(), new KafkaMetricsConsumerInstrumentation());
  }
}
