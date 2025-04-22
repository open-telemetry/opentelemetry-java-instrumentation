/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.extensionannotations;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasParameters;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.whereAny;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class WithSpanInstrumentation implements TypeInstrumentation {

  private static final String TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG =
      "otel.instrumentation.opentelemetry-annotations.exclude-methods";

  private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;
  private final ElementMatcher.Junction<MethodDescription> annotatedParametersMatcher;
  // this matcher matches all methods that should be excluded from transformation
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  WithSpanInstrumentation() {
    annotatedMethodMatcher =
        isAnnotatedWith(named("application.io.opentelemetry.extension.annotations.WithSpan"));
    annotatedParametersMatcher =
        hasParameters(
            whereAny(
                isAnnotatedWith(
                    named("application.io.opentelemetry.extension.annotations.SpanAttribute"))));
    excludedMethodsMatcher = configureExcludedMethods();
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("application.io.opentelemetry.extension.annotations.WithSpan");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(annotatedMethodMatcher);
  }

  @Override
  public void transform(TypeTransformer transformer) {
    ElementMatcher.Junction<MethodDescription> tracedMethods =
        annotatedMethodMatcher.and(not(excludedMethodsMatcher));

    ElementMatcher.Junction<MethodDescription> tracedMethodsWithParameters =
        tracedMethods.and(annotatedParametersMatcher);
    ElementMatcher.Junction<MethodDescription> tracedMethodsWithoutParameters =
        tracedMethods.and(not(annotatedParametersMatcher));

    transformer.applyAdviceToMethod(
        tracedMethodsWithoutParameters,
        WithSpanInstrumentation.class.getName() + "$WithSpanAdvice");

    // Only apply advice for tracing parameters as attributes if any of the parameters are annotated
    // with @SpanAttribute to avoid unnecessarily copying the arguments into an array.
    transformer.applyAdviceToMethod(
        tracedMethodsWithParameters,
        WithSpanInstrumentation.class.getName() + "$WithSpanAttributesAdvice");
  }

  /*
  Returns a matcher for all methods that should be excluded from auto-instrumentation by
  annotation-based advices.
  */
  static ElementMatcher.Junction<MethodDescription> configureExcludedMethods() {
    ElementMatcher.Junction<MethodDescription> result = none();

    Map<String, Set<String>> excludedMethods =
        MethodsConfigurationParser.parse(
            AgentInstrumentationConfig.get().getString(TRACE_ANNOTATED_METHODS_EXCLUDE_CONFIG));
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

  @SuppressWarnings("unused")
  public static class WithSpanAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method originMethod,
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to local variable so that there would be only one call to Class.getMethod.
      method = originMethod;

      Instrumenter<Method, Object> instrumenter = WithSpanSingletons.instrumenter();
      Context current = Java8BytecodeBridge.currentContext();

      if (instrumenter.shouldStart(current, method)) {
        context = instrumenter.start(current, method);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();

      AsyncOperationEndSupport<Method, Object> operationEndSupport =
          AsyncOperationEndSupport.create(
              WithSpanSingletons.instrumenter(), Object.class, method.getReturnType());
      returnValue = operationEndSupport.asyncEnd(context, method, returnValue, throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class WithSpanAttributesAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method originMethod,
        @Advice.Local("otelMethod") Method method,
        @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] args,
        @Advice.Local("otelRequest") MethodRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to local variable so that there would be only one call to Class.getMethod.
      method = originMethod;

      Instrumenter<MethodRequest, Object> instrumenter =
          WithSpanSingletons.instrumenterWithAttributes();
      Context current = Java8BytecodeBridge.currentContext();
      request = new MethodRequest(method, args);

      if (instrumenter.shouldStart(current, request)) {
        context = instrumenter.start(current, request);
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("otelMethod") Method method,
        @Advice.Local("otelRequest") MethodRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();
      AsyncOperationEndSupport<MethodRequest, Object> operationEndSupport =
          AsyncOperationEndSupport.create(
              WithSpanSingletons.instrumenterWithAttributes(),
              Object.class,
              method.getReturnType());
      returnValue = operationEndSupport.asyncEnd(context, request, returnValue, throwable);
    }
  }
}
