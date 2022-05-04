/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_5;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxKafkaInstrumentationModule extends InstrumentationModule {

  public VertxKafkaInstrumentationModule() {
    super("vertx-kafka-client", "vertx-kafka-client-3.5", "vertx");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KafkaReadStreamImplInstrumentation(), new KafkaConsumerRecordsImplInstrumentation());
  }
}
