/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class JerseyInstrumentationModule extends InstrumentationModule {
  public JerseyInstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "jersey", "jersey-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JerseyRequestContextInstrumentation(),
        new JerseyServletContainerInstrumentation(),
        new JerseyResourceMethodDispatcherInstrumentation());
  }
}
