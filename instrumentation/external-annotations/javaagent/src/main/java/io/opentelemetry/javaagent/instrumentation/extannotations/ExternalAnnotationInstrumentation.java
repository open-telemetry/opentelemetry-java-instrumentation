/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extannotations;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.extannotations.ExternalAnnotationSingletons.instrumenter;
import static java.util.logging.Level.WARNING;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class ExternalAnnotationInstrumentation implements TypeInstrumentation {

  private static final Logger logger =
      Logger.getLogger(ExternalAnnotationInstrumentationModule.class.getName());

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

  private final ElementMatcher.Junction<ClassLoader> classLoaderOptimization;
  private final ElementMatcher.Junction<NamedElement> traceAnnotationMatcher;
  /** This matcher matches all methods that should be excluded from transformation. */
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  public ExternalAnnotationInstrumentation() {
    Set<String> additionalTraceAnnotations = configureAdditionalTraceAnnotations(Config.get());

    if (additionalTraceAnnotations.isEmpty()) {
      classLoaderOptimization = none();
      traceAnnotationMatcher = none();
    } else {
      ElementMatcher.Junction<ClassLoader> classLoaderMatcher = none();
      for (String annotationName : additionalTraceAnnotations) {
        classLoaderMatcher = classLoaderMatcher.or(hasClassesNamed(annotationName));
      }
      this.classLoaderOptimization = classLoaderMatcher;
      this.traceAnnotationMatcher = namedOneOf(additionalTraceAnnotations.toArray(new String[0]));
    }

    excludedMethodsMatcher = configureExcludedMethods();
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return classLoaderOptimization;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(isAnnotatedWith(traceAnnotationMatcher));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isAnnotatedWith(traceAnnotationMatcher).and(not(excludedMethodsMatcher)),
        ExternalAnnotationInstrumentation.class.getName() + "$ExternalAnnotationAdvice");
  }

  private static Set<String> configureAdditionalTraceAnnotations(Config config) {
    String configString = config.getString(TRACE_ANNOTATIONS_CONFIG);
    if (configString == null) {
      return Collections.unmodifiableSet(new HashSet<>(DEFAULT_ANNOTATIONS));
    } else if (configString.isEmpty()) {
      return Collections.emptySet();
    } else if (!configString.matches(CONFIG_FORMAT)) {
      logger.log(
          WARNING,
          "Invalid trace annotations config '{0}'. Must match 'package.Annotation$Name;*'.",
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
            Config.get().getString(TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG));
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

  @SuppressWarnings("unused")
  public static class ExternalAnnotationAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelRequest") ClassAndMethod request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      request = ClassAndMethod.create(declaringClass, methodName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelRequest") ClassAndMethod request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Thrown Throwable throwable) {

      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
