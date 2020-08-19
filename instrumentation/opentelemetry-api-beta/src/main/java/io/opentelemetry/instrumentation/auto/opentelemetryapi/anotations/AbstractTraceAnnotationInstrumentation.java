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

package io.opentelemetry.instrumentation.auto.opentelemetryapi.anotations;

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
        MethodsConfigurationParser.parse(Config.get().getTraceAnnotatedMethodsExclude());
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
