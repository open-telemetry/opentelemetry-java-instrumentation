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
package io.opentelemetry.auto.instrumentation.traceannotation;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.contrib.auto.annotations.WithSpan;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Instrumentation for methods annotated with {@link
 * io.opentelemetry.contrib.auto.annotations.WithSpan} annotation. As that is Otel annotation, we
 * provide full support for all its attributes, as opposed to bare minimum functionality of {@link
 * TraceAnnotationsInstrumentation} for third party annotations.
 */
@Slf4j
@AutoService(Instrumenter.class)
public final class WithSpanAnnotationInstrumentation extends Instrumenter.Default {

  private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;

  public WithSpanAnnotationInstrumentation() {
    super("api-contrib");
    annotatedMethodMatcher = isAnnotatedWith(WithSpan.class);
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
    return singletonMap(annotatedMethodMatcher, packageName + ".WithSpanAdvice");
  }
}
