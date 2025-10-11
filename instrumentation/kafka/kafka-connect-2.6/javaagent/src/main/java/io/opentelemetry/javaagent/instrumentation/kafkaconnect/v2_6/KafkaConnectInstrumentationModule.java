/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaConnectInstrumentationModule extends InstrumentationModule {

  public KafkaConnectInstrumentationModule() {
    super("kafka-connect", "kafka-connect-2.6");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new SinkTaskInstrumentation(), new WorkerSinkTaskInstrumentation());
  }
}
