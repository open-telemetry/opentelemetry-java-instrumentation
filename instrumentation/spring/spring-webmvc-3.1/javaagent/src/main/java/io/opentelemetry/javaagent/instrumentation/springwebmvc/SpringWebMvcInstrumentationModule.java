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
public final class SpringWebMvcInstrumentationModule extends InstrumentationModule {
  public SpringWebMvcInstrumentationModule() {
    super("spring-webmvc", "spring-webmvc-3.1");
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
