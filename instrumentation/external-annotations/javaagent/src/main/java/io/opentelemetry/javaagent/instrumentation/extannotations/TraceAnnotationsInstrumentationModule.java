/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.safeHasSuperType;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(InstrumentationModule.class)
public class TraceAnnotationsInstrumentationModule extends InstrumentationModule {
  private static final Logger log =
      LoggerFactory.getLogger(TraceAnnotationsInstrumentationModule.class);

  private static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.$]+";

  static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\s*;?\\s*";

  private static final List<String> DEFAULT_ANNOTATIONS =
      Arrays.asList(
          "com.appoptics.api.ext.LogMethod",
          "com.newrelic.api.agent.Trace",
          "com.signalfx.tracing.api.Trace",
          "com.tracelytics.api.ext.LogMethod",
          "datadog.trace.api.Trace",
          "io.opentracing.contrib.dropwizard.Trace",
          "kamon.annotation.Trace",
          "kamon.annotation.api.Trace",
          "org.springframework.cloud.sleuth.annotation.NewSpan");

  private static final String TRACE_ANNOTATIONS_CONFIG =
      "otel.instrumentation.external-annotations.include";
  private static final String TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG =
      "otel.instrumentation.external-annotations.exclude-methods";

  public TraceAnnotationsInstrumentationModule() {
    super("external-annotations");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AnnotatedMethodsInstrumentation());
  }

  public static class AnnotatedMethodsInstrumentation implements TypeInstrumentation {
    private final Set<String> additionalTraceAnnotations;
    private final ElementMatcher.Junction<ClassLoader> classLoaderOptimization;
    private final ElementMatcher.Junction<NamedElement> traceAnnotationMatcher;
    /** This matcher matches all methods that should be excluded from transformation. */
    private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

    public AnnotatedMethodsInstrumentation() {
      additionalTraceAnnotations = configureAdditionalTraceAnnotations(Config.get());

      if (additionalTraceAnnotations.isEmpty()) {
        classLoaderOptimization = none();
        traceAnnotationMatcher = none();
      } else {
        ElementMatcher.Junction<ClassLoader> classLoaderMatcher = null;
        ElementMatcher.Junction<NamedElement> methodTraceMatcher = null;
        for (String annotationName : additionalTraceAnnotations) {
          if (methodTraceMatcher == null) {
            classLoaderMatcher = hasClassesNamed(annotationName);
            methodTraceMatcher = named(annotationName);
          } else {
            classLoaderMatcher = classLoaderMatcher.or(hasClassesNamed(annotationName));
            methodTraceMatcher = methodTraceMatcher.or(named(annotationName));
          }
        }
        this.classLoaderOptimization = classLoaderMatcher;
        this.traceAnnotationMatcher = methodTraceMatcher;
      }

      excludedMethodsMatcher = configureExcludedMethods();
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return classLoaderOptimization;
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(declaresMethod(isAnnotatedWith(traceAnnotationMatcher)));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isAnnotatedWith(traceAnnotationMatcher).and(not(excludedMethodsMatcher)),
          TraceAdvice.class.getName());
    }

    private static Set<String> configureAdditionalTraceAnnotations(Config config) {
      String configString = config.getProperty(TRACE_ANNOTATIONS_CONFIG);
      if (configString == null) {
        return Collections.unmodifiableSet(new HashSet<>(DEFAULT_ANNOTATIONS));
      } else if (configString.isEmpty()) {
        return Collections.emptySet();
      } else if (!configString.matches(CONFIG_FORMAT)) {
        log.warn(
            "Invalid trace annotations config '{}'. Must match 'package.Annotation$Name;*'.",
            configString);
        return Collections.emptySet();
      } else {
        Set<String> annotations = new HashSet<>();
        String[] annotationClasses = configString.split(";", -1);
        for (String annotationClass : annotationClasses) {
          if (!annotationClass.trim().isEmpty()) {
            annotations.add(annotationClass.trim());
          }
        }
        return Collections.unmodifiableSet(annotations);
      }
    }

    /**
     * Returns a matcher for all methods that should be excluded from auto-instrumentation by
     * annotation-based advices.
     */
    private static ElementMatcher.Junction<MethodDescription> configureExcludedMethods() {
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
