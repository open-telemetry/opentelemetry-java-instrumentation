/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.rmi.context.client.RmiClientContextInstrumentation;
import io.opentelemetry.javaagent.instrumentation.rmi.context.server.RmiServerContextInstrumentation;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;

@AutoService(InstrumentationModule.class)
public class RmiContextPropagationInstrumentationModule extends InstrumentationModule {
  public RmiContextPropagationInstrumentationModule() {
    super("rmi", "rmi-context-propagation");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPayload$InjectAdapter",
      "io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPayload$ExtractAdapter",
      "io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPayload",
      "io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPropagator",
      "io.opentelemetry.javaagent.instrumentation.rmi.context.server.ContextDispatcher",
      "io.opentelemetry.javaagent.instrumentation.rmi.context.server.ContextDispatcher$NoopRemote"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RmiClientContextInstrumentation(), new RmiServerContextInstrumentation());
  }

  @Override
  public Map<String, String> contextStore() {
    // caching if a connection can support enhanced format
    return singletonMap("sun.rmi.transport.Connection", "java.lang.Boolean");
  }
}
