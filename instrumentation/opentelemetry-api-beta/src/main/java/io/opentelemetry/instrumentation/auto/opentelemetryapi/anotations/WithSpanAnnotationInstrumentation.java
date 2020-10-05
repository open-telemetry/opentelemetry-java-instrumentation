/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for methods annotated with {@link
 * io.opentelemetry.extensions.auto.annotations.WithSpan} annotation.
 */
@AutoService(Instrumenter.class)
public final class WithSpanAnnotationInstrumentation
    extends AbstractTraceAnnotationInstrumentation {

  private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;
  /*
  This matcher matches all methods that should be excluded from transformation
   */
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  public WithSpanAnnotationInstrumentation() {
    super("trace", "with-span-annotation");
    annotatedMethodMatcher =
        isAnnotatedWith(named("application.io.opentelemetry.extensions.auto.annotations.WithSpan"));
    excludedMethodsMatcher = configureExcludedMethods();
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(annotatedMethodMatcher);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TraceAnnotationTracer",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        annotatedMethodMatcher.and(not(excludedMethodsMatcher)), packageName + ".WithSpanAdvice");
  }
}
