/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Instrumentation for methods annotated with {@link
 * application.io.opentelemetry.extension.annotations.WithSpan} annotation.
 */
@AutoService(InstrumentationModule.class)
public class WithSpanInstrumentationModule extends InstrumentationModule {

  public WithSpanInstrumentationModule() {
    super("opentelemetry-extension-annotations", "opentelemetry-extension-annotations-1.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WithSpanInstrumentation());
  }
}
