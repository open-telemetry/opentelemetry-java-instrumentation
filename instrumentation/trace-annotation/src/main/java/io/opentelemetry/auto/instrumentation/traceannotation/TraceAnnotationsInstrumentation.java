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

import static io.opentelemetry.auto.instrumentation.traceannotation.TraceConfigInstrumentation.PACKAGE_CLASS_NAME_REGEX;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import io.opentelemetry.auto.config.Config;
import io.opentelemetry.auto.tooling.Instrumenter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Slf4j
@AutoService(Instrumenter.class)
public final class TraceAnnotationsInstrumentation extends Instrumenter.Default {

  static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;?\\s*";

  private static final String[] DEFAULT_ANNOTATIONS =
      new String[] {
        "com.appoptics.api.ext.LogMethod",
        "com.newrelic.api.agent.Trace",
        "com.signalfx.tracing.api.Trace",
        "com.tracelytics.api.ext.LogMethod",
        "datadog.trace.api.Trace",
        "io.opentracing.contrib.dropwizard.Trace",
        "kamon.annotation.Trace",
        "kamon.annotation.api.Trace",
        "org.springframework.cloud.sleuth.annotation.NewSpan"
      };

  private final Set<String> additionalTraceAnnotations;
  private final ElementMatcher.Junction<NamedElement> methodTraceMatcher;

  public TraceAnnotationsInstrumentation() {
    super("trace", "trace-annotation");

    final String configString = Config.get().getTraceAnnotations();
    if (configString == null) {
      additionalTraceAnnotations =
          Collections.unmodifiableSet(Sets.newHashSet(DEFAULT_ANNOTATIONS));
    } else if (configString.trim().isEmpty()) {
      additionalTraceAnnotations = Collections.emptySet();
    } else if (!configString.matches(CONFIG_FORMAT)) {
      log.warn(
          "Invalid trace annotations config '{}'. Must match 'package.Annotation$Name;*'.",
          configString);
      additionalTraceAnnotations = Collections.emptySet();
    } else {
      final Set<String> annotations = Sets.newHashSet();
      final String[] annotationClasses = configString.split(";", -1);
      for (final String annotationClass : annotationClasses) {
        if (!annotationClass.trim().isEmpty()) {
          annotations.add(annotationClass.trim());
        }
      }
      additionalTraceAnnotations = Collections.unmodifiableSet(annotations);
    }

    if (additionalTraceAnnotations.isEmpty()) {
      methodTraceMatcher = none();
    } else {
      ElementMatcher.Junction<NamedElement> methodTraceMatcher = null;
      for (final String annotationName : additionalTraceAnnotations) {
        if (methodTraceMatcher == null) {
          methodTraceMatcher = named(annotationName);
        } else {
          methodTraceMatcher = methodTraceMatcher.or(named(annotationName));
        }
      }
      this.methodTraceMatcher = methodTraceMatcher;
    }
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    ElementMatcher.Junction<ClassLoader> matcher = null;
    for (final String name : additionalTraceAnnotations) {
      if (matcher == null) {
        matcher = hasClassesNamed(name);
      } else {
        matcher = matcher.or(hasClassesNamed(name));
      }
    }
    if (matcher == null) {
      return none();
    }
    return matcher;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(declaresMethod(isAnnotatedWith(methodTraceMatcher)));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".TraceDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(isAnnotatedWith(methodTraceMatcher), packageName + ".TraceAdvice");
  }
}
