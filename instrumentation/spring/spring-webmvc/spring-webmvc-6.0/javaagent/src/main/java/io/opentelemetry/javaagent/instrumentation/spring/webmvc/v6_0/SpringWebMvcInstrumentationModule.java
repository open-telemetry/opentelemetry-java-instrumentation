/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringWebMvcInstrumentationModule extends InstrumentationModule {
  public SpringWebMvcInstrumentationModule() {
    super("spring-webmvc", "spring-webmvc-6.0");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith(
        "org.springframework.web.servlet.v6_0.OpenTelemetryHandlerMappingFilter");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new DispatcherServletInstrumentation(), new HandlerAdapterInstrumentation());
  }
}
