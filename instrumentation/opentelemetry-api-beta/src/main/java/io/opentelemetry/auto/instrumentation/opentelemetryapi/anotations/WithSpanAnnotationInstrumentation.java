/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.opentelemetryapi.anotations;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
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
      packageName + ".TraceDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        annotatedMethodMatcher.and(not(excludedMethodsMatcher)), packageName + ".WithSpanAdvice");
  }
}
