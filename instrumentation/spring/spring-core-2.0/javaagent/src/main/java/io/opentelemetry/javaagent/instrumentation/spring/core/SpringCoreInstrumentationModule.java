/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.core;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringCoreInstrumentationModule extends InstrumentationModule {
  public SpringCoreInstrumentationModule() {
    super("spring-core", "spring-core-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.springframework.core.task.SimpleAsyncTaskExecutor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new SimpleAsyncTaskExecutorInstrumentation());
  }
}
