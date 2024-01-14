/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.rmi.context.client.RmiClientContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.rmi.context.server.RmiServerContextInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RmiContextPropagationInstrumentationModule extends InstrumentationModule {
  public RmiContextPropagationInstrumentationModule() {
    super("rmi", "rmi-context-propagation");
  }

  @Override
  public boolean isIndyModule() {
    // java.lang.IllegalAccessError: class
    // io.opentelemetry.javaagent.instrumentation.rmi.context.client.RmiClientContextInstrumentation$StreamRemoteCallConstructorAdvice (in unnamed module @0x740ee00f) cannot access class sun.rmi.transport.Connection (in module java.rmi) because module java.rmi does not export sun.rmi.transport to unnamed module @0x740ee00f
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RmiClientContextInstrumentation(), new RmiServerContextInstrumentation());
  }
}
