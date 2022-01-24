/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.reactor;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesReactorInstrumentationModule extends InstrumentationModule {

  public KotlinCoroutinesReactorInstrumentationModule() {
    super("kotlinx-coroutines", "kotlinx-coroutines-reactor");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new KotlinMonoCoroutineInstrumentation(), new KotlinPublisherCoroutineInstrumentation());
  }
}
