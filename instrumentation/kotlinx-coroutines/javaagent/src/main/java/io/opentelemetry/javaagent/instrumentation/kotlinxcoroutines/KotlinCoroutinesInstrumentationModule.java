/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
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
  public boolean isIndyModule() {
    //  java.lang.LinkageError: bad method type alias: (CoroutineContext)Object[] not visible from
    // class
    // io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.KotlinCoroutinesInstrumentation$ContextAdvice
    // CoroutineContext is loaded from agent class loader not application
    return false;
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new KotlinCoroutinesInstrumentation());
  }
}
