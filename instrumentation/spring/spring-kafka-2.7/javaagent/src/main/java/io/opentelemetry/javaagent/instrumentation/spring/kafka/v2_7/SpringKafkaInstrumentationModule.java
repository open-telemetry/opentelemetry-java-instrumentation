/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringKafkaInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringKafkaInstrumentationModule() {
    super("spring-kafka", "spring-kafka-2.7");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractMessageListenerContainerInstrumentation(),
        new ListenerConsumerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
