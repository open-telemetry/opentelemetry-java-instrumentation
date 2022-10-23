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
public class ResteasyInstrumentationModule extends InstrumentationModule {
  public ResteasyInstrumentationModule() {
    super("jaxrs", "jaxrs-3.0", "resteasy", "resteasy-6.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ResteasyRequestContextInstrumentation(),
        new ResteasyServletContainerDispatcherInstrumentation(),
        new ResteasyRootNodeTypeInstrumentation(),
        new ResteasyResourceMethodInvokerInstrumentation(),
        new ResteasyResourceLocatorInvokerInstrumentation());
  }
}
