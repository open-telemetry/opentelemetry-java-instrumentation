/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.traceannotation;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.config.MethodsConfigurationParser;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public abstract class AbstractTraceAnnotationInstrumentation extends Instrumenter.Default {
  private static final String TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG =
      "otel.trace.annotated.methods.exclude";

  public AbstractTraceAnnotationInstrumentation(
      String instrumentationName, String... additionalNames) {
    super(instrumentationName, additionalNames);
  }

  /*
  Returns a matcher for all methods that should be excluded from auto-instrumentation by
  annotation-based advices.
  */
  ElementMatcher.Junction<MethodDescription> configureExcludedMethods() {
    ElementMatcher.Junction<MethodDescription> result = none();

    Map<String, Set<String>> excludedMethods =
        MethodsConfigurationParser.parse(
            Config.get().getProperty(TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG));
    for (Map.Entry<String, Set<String>> entry : excludedMethods.entrySet()) {
      String className = entry.getKey();
      ElementMatcher.Junction<ByteCodeElement> classMather =
          isDeclaredBy(ElementMatchers.<TypeDescription>named(className));

      ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher = none();
      for (String methodName : entry.getValue()) {
        excludedMethodsMatcher = excludedMethodsMatcher.or(ElementMatchers.named(methodName));
      }

      result = result.or(classMather.and(excludedMethodsMatcher));
    }

    return result;
  }
}
