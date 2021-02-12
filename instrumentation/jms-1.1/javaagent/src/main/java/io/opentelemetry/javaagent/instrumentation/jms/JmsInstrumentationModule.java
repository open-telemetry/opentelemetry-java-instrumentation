/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

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

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("javax.jms.MessageConsumer", MessageDestination.class.getName());
  }
}
