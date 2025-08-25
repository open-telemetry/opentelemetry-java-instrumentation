/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import java.util.logging.Logger;

@AutoService(InstrumentationModule.class)
public class KafkaConnectInstrumentationModule extends InstrumentationModule {

  private static final Logger logger =
      Logger.getLogger(KafkaConnectInstrumentationModule.class.getName());

  public KafkaConnectInstrumentationModule() {
    super("kafka-connect", "kafka-connect-2.6");
    logger.info("KafkaConnect: InstrumentationModule constructor called");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    logger.info("KafkaConnect: typeInstrumentations() called");
    return singletonList(new SinkTaskInstrumentation());
  }
}
