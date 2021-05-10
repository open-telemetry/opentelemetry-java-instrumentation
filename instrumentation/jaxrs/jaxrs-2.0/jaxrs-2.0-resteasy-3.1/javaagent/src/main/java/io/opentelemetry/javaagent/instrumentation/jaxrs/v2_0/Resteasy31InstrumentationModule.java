/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class Resteasy31InstrumentationModule extends InstrumentationModule {
  public Resteasy31InstrumentationModule() {
    super("jaxrs", "jaxrs-2.0", "resteasy", "resteasy-3.1");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new Resteasy31RequestContextInstrumentation(),
        new ResteasyServletContainerDispatcherInstrumentation(),
        new ResteasyRootNodeTypeInstrumentation(),
        new ResteasyResourceMethodInvokerInstrumentation(),
        new ResteasyResourceLocatorInvokerInstrumentation());
  }
}
