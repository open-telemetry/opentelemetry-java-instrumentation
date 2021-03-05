/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

import application.io.opentelemetry.extension.annotations.WithSpan;
import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/** Instrumentation for methods annotated with {@link WithSpan} annotation. */
@AutoService(InstrumentationModule.class)
public class WithSpanInstrumentationModule extends InstrumentationModule {

  private static final String TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG =
      "otel.instrumentation.opentelemetry-annotations.exclude-methods";

  public WithSpanInstrumentationModule() {
    super("opentelemetry-annotations");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AnnotatedMethodInstrumentation());
  }

  public static class AnnotatedMethodInstrumentation implements TypeInstrumentation {
    private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;
    // this matcher matches all methods that should be excluded from transformation
    private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

    AnnotatedMethodInstrumentation() {
      annotatedMethodMatcher =
          isAnnotatedWith(named("application.io.opentelemetry.extension.annotations.WithSpan"));
      excludedMethodsMatcher = configureExcludedMethods();
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return declaresMethod(annotatedMethodMatcher);
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          annotatedMethodMatcher.and(not(excludedMethodsMatcher)), WithSpanAdvice.class.getName());
    }

    /*
    Returns a matcher for all methods that should be excluded from auto-instrumentation by
    annotation-based advices.
    */
    static ElementMatcher.Junction<MethodDescription> configureExcludedMethods() {
      ElementMatcher.Junction<MethodDescription> result = none();

      Map<String, Set<String>> excludedMethods =
          MethodsConfigurationParser.parse(
              Config.get().getProperty(TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG));
      for (Map.Entry<String, Set<String>> entry : excludedMethods.entrySet()) {
        String className = entry.getKey();
        ElementMatcher.Junction<ByteCodeElement> classMather =
            isDeclaredBy(ElementMatchers.named(className));

        ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher = none();
        for (String methodName : entry.getValue()) {
          excludedMethodsMatcher = excludedMethodsMatcher.or(ElementMatchers.named(methodName));
        }

        result = result.or(classMather.and(excludedMethodsMatcher));
      }

      return result;
    }
  }
}
