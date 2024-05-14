/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.counted;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for methods annotated with {@link Counted} or {@link MetricAttribute}
 * annotations.
 */
@AutoService(InstrumentationModule.class)
public class CountedInstrumentationModule extends InstrumentationModule {

  public CountedInstrumentationModule() {
    super("opentelemetry-instrumentation-annotation-counted", "counted");
  }

  @Override
  public int order() {
    // Run first to ensure other automatic instrumentation is added after and therefore is executed
    // earlier in the instrumented method and create the span to attach attributes to.
    return -1000;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("application.io.opentelemetry.instrumentation.annotations.Counted");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CountedInstrumentation());
  }
}
