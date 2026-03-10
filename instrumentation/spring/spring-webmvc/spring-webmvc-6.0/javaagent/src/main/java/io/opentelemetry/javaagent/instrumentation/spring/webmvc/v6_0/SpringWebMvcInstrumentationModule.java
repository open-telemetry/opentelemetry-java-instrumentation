/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webmvc.v6_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.InjectionMode;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringWebMvcInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringWebMvcInstrumentationModule() {
    super("spring-webmvc", "spring-webmvc-6.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("jakarta.servlet.Filter");
  }

  @Override
  public boolean isHelperClass(String className) {
    // filter on prefix due to inner classes
    return className.startsWith(
        "org.springframework.web.servlet.v6_0.OpenTelemetryHandlerMappingFilter");
  }

  @Override
  public void injectClasses(ClassInjector injector) {
    injector
        .proxyBuilder("org.springframework.web.servlet.v6_0.OpenTelemetryHandlerMappingFilter")
        .inject(InjectionMode.CLASS_AND_RESOURCE);
  }

  @Override
  public String getModuleGroup() {
    return "servlet";
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new DispatcherServletInstrumentation(), new HandlerAdapterInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
