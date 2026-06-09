/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations.v1_16;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.V3PreviewFallbackEnabledInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
@SuppressWarnings("deprecation") // using v3 preview fallback helper until 3.0
public class AnnotationInstrumentationModule extends V3PreviewFallbackEnabledInstrumentationModule {

  public AnnotationInstrumentationModule() {
    super(
        "opentelemetry-instrumentation-annotations",
        "opentelemetry-instrumentation-annotations-1.16",
        "annotations");
  }

  @Override
  public int order() {
    // Run after other instrumentations.
    return 1000;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.16.0
    return hasClassesNamed("application.io.opentelemetry.instrumentation.annotations.WithSpan");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new WithSpanInstrumentation(), new AddingSpanAttributesInstrumentation());
  }
}
