/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public final class AnnotationExcludedMethods {

  /*
  Returns a matcher for all methods that should be excluded from auto-instrumentation by
  annotation-based advices.
  */
  public static ElementMatcher.Junction<MethodDescription> configureExcludedMethods() {
    ElementMatcher.Junction<MethodDescription> result = none();

    Map<String, Set<String>> excludedMethods =
        MethodsConfigurationParser.parse(
            DeclarativeConfigUtil.getString(
                    GlobalOpenTelemetry.get(),
                    "java",
                    "opentelemetry-instrumentation-annotations",
                    "exclude_methods")
                .orElse(null));
    for (Map.Entry<String, Set<String>> entry : excludedMethods.entrySet()) {
      String className = entry.getKey();
      ElementMatcher.Junction<ByteCodeElement> matcher =
          isDeclaredBy(ElementMatchers.named(className));

      Set<String> methodNames = entry.getValue();
      if (!methodNames.isEmpty()) {
        matcher = matcher.and(namedOneOf(methodNames.toArray(new String[0])));
      }

      result = result.or(matcher);
    }

    return result;
  }

  private AnnotationExcludedMethods() {}
}
