/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.reactor.KotlinCoroutinesFluxInstrumentation;
import io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.reactor.KotlinCoroutinesMonoInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesInstrumentationModule() {
    super("kotlinx-coroutines");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KotlinCoroutinesInstrumentation(),
        new KotlinCoroutinesMonoInstrumentation(),
        new KotlinCoroutinesFluxInstrumentation());
  }
}
