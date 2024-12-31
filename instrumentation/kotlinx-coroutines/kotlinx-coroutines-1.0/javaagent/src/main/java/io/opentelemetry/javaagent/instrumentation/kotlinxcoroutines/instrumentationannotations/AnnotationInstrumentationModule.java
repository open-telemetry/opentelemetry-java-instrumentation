/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.instrumentationannotations;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/** Instrumentation for methods annotated with {@code WithSpan} annotation. */
@AutoService(InstrumentationModule.class)
public class AnnotationInstrumentationModule extends InstrumentationModule {

  public AnnotationInstrumentationModule() {
    super(
        "kotlinx-coroutines",
        "kotlinx-coroutines-1.0",
        "kotlinx-coroutines-opentelemetry-instrumentation-annotations",
        "opentelemetry-instrumentation-annotations");
  }

  @Override
  public boolean isIndyModule() {
    // needs helper classes in the same class loader
    return false;
  }

  @Override
  public int order() {
    // Run first to ensure other automatic instrumentation is added after and therefore is executed
    // earlier in the instrumented method and create the span to attach attributes to.
    return -1000;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        "application.io.opentelemetry.instrumentation.annotations.WithSpan",
        "kotlinx.coroutines.CoroutineContextKt");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WithSpanInstrumentation());
  }
}
