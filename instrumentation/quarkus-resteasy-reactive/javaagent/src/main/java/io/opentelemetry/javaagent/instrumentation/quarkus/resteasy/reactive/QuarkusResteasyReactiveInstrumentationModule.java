/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.quarkus.resteasy.reactive;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class QuarkusResteasyReactiveInstrumentationModule extends InstrumentationModule {

  public QuarkusResteasyReactiveInstrumentationModule() {
    super("quarkus", "jaxrs", "quarkus-resteasy-reactive", "quarkus-resteasy-reactive-3.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractResteasyReactiveContextInstrumentation(),
        new InvocationHandlerInstrumentation());
  }
}
