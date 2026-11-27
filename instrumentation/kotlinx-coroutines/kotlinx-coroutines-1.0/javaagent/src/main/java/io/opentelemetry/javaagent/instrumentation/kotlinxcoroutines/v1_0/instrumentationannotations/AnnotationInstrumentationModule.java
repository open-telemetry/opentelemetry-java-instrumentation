/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations;

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
    super("kotlinx-coroutines-annotations");
  }

  @Override
  public int order() {
    // Run after other instrumentations.
    return 1000;
  }

  @Override
  public boolean defaultEnabled() {
    return false;
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.extension.kotlin.");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 1.6.1, where an undispatched withContext keeps the thread context it restores
        // per thread (Kotlin/kotlinx.coroutines#3155); with older versions a concurrent resume can
        // skip that restore (Kotlin/kotlinx.coroutines#2930), and with it the scopes the runtime
        // marker closes
        "kotlinx.coroutines.internal.ResizableAtomicArray",
        // artifact presence gate
        // added in opentelemetry-instrumentation-annotations 1.16.0
        "application.io.opentelemetry.instrumentation.annotations.WithSpan");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new WithSpanInstrumentation());
  }

  @Override
  public List<String> exposedClassNames() {
    // AnnotationInstrumentationHelper is called directly in the instrumented bytecode.
    return singletonList(
        "io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations.AnnotationInstrumentationHelper");
  }
}
