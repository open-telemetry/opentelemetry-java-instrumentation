/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v2_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import application.io.opentelemetry.instrumentation.annotations.Counted;
import application.io.opentelemetry.instrumentation.annotations.MetricAttribute;
import application.io.opentelemetry.instrumentation.annotations.Timed;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for methods annotated with {@link Counted}, {@link Timed} and {@link
 * MetricAttribute} annotations.
 */
@AutoService(InstrumentationModule.class)
public class MetricsAnnotationInstrumentationModule extends InstrumentationModule {

  public MetricsAnnotationInstrumentationModule() {
    super("opentelemetry-instrumentation-annotations", "metrics-annotations");
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
  public boolean isIndyModule() {
    // TimedInstrumentation does not work with indy
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CountedInstrumentation(), new TimedInstrumentation());
  }
}
