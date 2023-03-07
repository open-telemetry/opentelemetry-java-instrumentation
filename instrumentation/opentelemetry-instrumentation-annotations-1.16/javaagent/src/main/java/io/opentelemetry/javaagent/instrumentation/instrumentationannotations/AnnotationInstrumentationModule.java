/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static java.util.Arrays.asList;

import application.io.opentelemetry.instrumentation.annotations.WithSpan;
import application.io.opentelemetry.instrumentation.annotations.WithSpanAttributes;
import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Instrumentation for methods annotated with {@link WithSpan} and {@link WithSpanAttributes}
 * annotations.
 */
@AutoService(InstrumentationModule.class)
public class AnnotationInstrumentationModule extends InstrumentationModule {

  public AnnotationInstrumentationModule() {
    super("opentelemetry-instrumentation-annotations");
  }

  @Override
  public int order() {
    // Run first to ensure other automatic instrumentation is added after and therefore is executed
    // earlier in the instrumented method and create the span to attach attributes to.
    return -1000;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new WithSpanInstrumentation(), new WithSpanAttributesInstrumentation());
  }
}
