/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaStreamsInstrumentationModule extends InstrumentationModule {
  public KafkaStreamsInstrumentationModule() {
    super("kafka-streams", "kafka-streams-0.11", "kafka");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KafkaStreamsSourceNodeRecordDeserializerInstrumentation(),
        new StreamTaskStartInstrumentation(),
        new StreamTaskStopInstrumentation());
  }
}
