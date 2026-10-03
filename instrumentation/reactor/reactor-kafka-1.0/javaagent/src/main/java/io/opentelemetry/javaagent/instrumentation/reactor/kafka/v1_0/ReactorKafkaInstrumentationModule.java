/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReactorKafkaInstrumentationModule extends InstrumentationModule {

  public ReactorKafkaInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "reactor-kafka-1.0" : "reactor-kafka",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"reactor-kafka"}
            : new String[] {"reactor-kafka-1.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KafkaReceiverInstrumentation(),
        new ReceiverRecordInstrumentation(),
        new DefaultKafkaReceiverInstrumentation(),
        new ConsumerHandlerInstrumentation());
  }
}
