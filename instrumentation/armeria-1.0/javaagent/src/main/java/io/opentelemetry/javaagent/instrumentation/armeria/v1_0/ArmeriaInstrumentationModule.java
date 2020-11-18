/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.v1_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ArmeriaInstrumentationModule extends InstrumentationModule {
  public ArmeriaInstrumentationModule() {
    super("armeria", "armeria-1.0");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.javaagent.instrumentation.armeria.v1_0.ArmeriaDecorators",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.ArmeriaClientTracer$ArmeriaSetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient",
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.client.OpenTelemetryClient$1",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.ArmeriaServerTracer$ArmeriaGetter",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService",
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$Decorator",
      // Corresponds to lambda when calling .thenAccept(log -> ...
      "io.opentelemetry.instrumentation.armeria.v1_0.server.OpenTelemetryService$1",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ArmeriaWebClientBuilderInstrumentation(),
        new ArmeriaServerInstrumentation(),
        new ArmeriaServerBuilderInstrumentation());
  }
}
