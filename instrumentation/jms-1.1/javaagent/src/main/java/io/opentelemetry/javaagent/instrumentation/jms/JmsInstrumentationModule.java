/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JmsInstrumentationModule extends InstrumentationModule {
  public JmsInstrumentationModule() {
    super("jms", "jms-1.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JmsMessageConsumerInstrumentation(),
        new JmsMessageListenerInstrumentation(),
        new JmsMessageProducerInstrumentation());
  }
}
