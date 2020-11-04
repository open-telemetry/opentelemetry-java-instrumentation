/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springwebmvc;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public final class SpringMvcInstrumentationModule extends InstrumentationModule {
  public SpringMvcInstrumentationModule() {
    super("spring-mvc");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".SpringWebMvcTracer",
      packageName + ".HandlerMappingResourceNameFilter",
      packageName + ".HandlerMappingResourceNameFilter$BeanDefinition",
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new WebApplicationContextInstrumentation(),
        new DispatcherServletInstrumentation(),
        new HandlerAdapterInstrumentation());
  }
}
