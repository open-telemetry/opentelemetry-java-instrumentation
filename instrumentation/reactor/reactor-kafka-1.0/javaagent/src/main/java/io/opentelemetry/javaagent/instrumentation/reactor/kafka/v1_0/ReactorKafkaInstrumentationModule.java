/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ReactorKafkaInstrumentationModule extends InstrumentationModule {

  public ReactorKafkaInstrumentationModule() {
    super("reactor-kafka", "reactor-kafka-1.0");
  }

  @Override
  public boolean isIndyModule() {
    // OpenTelemetryMetricsReporter is not available in app class loader
    return false;
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
