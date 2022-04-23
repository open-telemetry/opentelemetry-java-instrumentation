/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.ArrayList;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PulsarInstrumentationModule extends InstrumentationModule {
  public PulsarInstrumentationModule() {
    super("apache-pulsar", "apache-pulsar-2.8.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    List<TypeInstrumentation> instrumentations = new ArrayList<>(4);
    instrumentations.add(new ConsumerImplInstrumentation());
    instrumentations.add(new MessageInstrumentation());
    instrumentations.add(new ProducerImplInstrumentation());
    instrumentations.add(new MessageListenerInstrumentation());
    return instrumentations;
  }
}
