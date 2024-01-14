/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v3_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringWebMvcInstrumentationModule extends InstrumentationModule {

  public SpringWebMvcInstrumentationModule() {
    super("spring-webmvc", "spring-webmvc-3.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("javax.servlet.Filter");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith(
        "org.springframework.web.servlet.v3_1.OpenTelemetryHandlerMappingFilter");
  }

  @Override
  public boolean isIndyModule() {
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new DispatcherServletInstrumentation(), new HandlerAdapterInstrumentation());
  }
}
