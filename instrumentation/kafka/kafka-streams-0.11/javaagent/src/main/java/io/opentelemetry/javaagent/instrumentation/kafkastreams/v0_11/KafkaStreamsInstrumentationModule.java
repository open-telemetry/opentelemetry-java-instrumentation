/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaStreamsInstrumentationModule extends InstrumentationModule {
  public KafkaStreamsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "kafka-streams-0.11" : "kafka-streams",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"kafka-streams", "kafka"}
            : new String[] {"kafka-streams-0.11", "kafka"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PartitionGroupInstrumentation(),
        new RecordDeserializerInstrumentation(),
        new SourceNodeRecordDeserializerInstrumentation(),
        new StreamTaskInstrumentation(),
        new StreamThreadInstrumentation());
  }
}
