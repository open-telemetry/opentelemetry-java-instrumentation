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
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ArmeriaWebClientBuilderInstrumentation(),
        new ArmeriaServerInstrumentation(),
        new ArmeriaServerBuilderInstrumentation());
  }
}
