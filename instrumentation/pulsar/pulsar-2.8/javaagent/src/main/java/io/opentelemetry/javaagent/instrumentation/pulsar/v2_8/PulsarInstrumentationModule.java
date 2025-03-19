/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Arrays;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class PulsarInstrumentationModule extends InstrumentationModule {
  public PulsarInstrumentationModule() {
    super("pulsar", "pulsar-2.8");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new ConsumerBaseInstrumentation(),
        new ConsumerImplInstrumentation(),
        new ProducerImplInstrumentation(),
        new MessageInstrumentation(),
        new MessageListenerInstrumentation(),
        new SendCallbackInstrumentation(),
        new TransactionImplInstrumentation());
  }
}
