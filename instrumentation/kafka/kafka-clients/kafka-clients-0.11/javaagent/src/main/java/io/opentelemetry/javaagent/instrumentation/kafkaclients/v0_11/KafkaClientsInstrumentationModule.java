/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaClientsInstrumentationModule extends InstrumentationModule {
  public KafkaClientsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "kafka-clients-0.11" : "kafka-clients",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"kafka-clients", "kafka"}
            : new String[] {"kafka-clients-0.11", "kafka"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KafkaProducerInstrumentation(),
        new KafkaConsumerInstrumentation(),
        new ConsumerRecordsInstrumentation());
  }
}
