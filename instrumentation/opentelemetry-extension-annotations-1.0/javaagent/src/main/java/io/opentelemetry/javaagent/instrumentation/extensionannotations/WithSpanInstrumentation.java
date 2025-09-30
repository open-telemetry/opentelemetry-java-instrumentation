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
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.whereAny;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.config.MethodsConfigurationParser;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
        tracedMethodsWithoutParameters.and(isMethod()),
        WithSpanInstrumentation.class.getName() + "$WithSpanAdvice");

    // Only apply advice for tracing parameters as attributes if any of the parameters are annotated
    // with @SpanAttribute to avoid unnecessarily copying the arguments into an array.
    transformer.applyAdviceToMethod(
        tracedMethodsWithParameters.and(isMethod()),
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

    public static class WithSpanAdviceScope {
      private final Method method;
      private final Context context;
      private final Scope scope;

      private WithSpanAdviceScope(Method method, Context context, Scope scope) {
        this.method = method;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static WithSpanAdviceScope start(Method method) {
        Instrumenter<Method, Object> instrumenter = WithSpanSingletons.instrumenter();
        Context current = Context.current();
        if (!instrumenter.shouldStart(current, method)) {
          return null;
        }
        Context context = instrumenter.start(current, method);
        return new WithSpanAdviceScope(method, context, context.makeCurrent());
      }

      public Object end(Object returnValue, @Nullable Throwable throwable) {
        scope.close();
        AsyncOperationEndSupport<Method, Object> operationEndSupport =
            AsyncOperationEndSupport.create(
                WithSpanSingletons.instrumenter(), Object.class, method.getReturnType());
        return operationEndSupport.asyncEnd(context, method, returnValue, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static WithSpanAdviceScope onEnter(@Advice.Origin Method originMethod) {
      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to advice scope so that there would be only one call to Class.getMethod.
      return WithSpanAdviceScope.start(originMethod);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Object stopSpan(
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returnValue,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable WithSpanAdviceScope adviceScope) {
      if (adviceScope != null) {
        return adviceScope.end(returnValue, throwable);
      }
      return returnValue;
    }
  }

  @SuppressWarnings("unused")
  public static class WithSpanAttributesAdvice {

    public static class WithSpanAttributesAdviceScope {
      private final Method method;
      private final MethodRequest request;
      private final Context context;
      private final Scope scope;

      private WithSpanAttributesAdviceScope(
          Method method, MethodRequest request, Context context, Scope scope) {
        this.method = method;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static WithSpanAttributesAdviceScope start(Method method, Object[] args) {
        MethodRequest request = new MethodRequest(method, args);
        Instrumenter<MethodRequest, Object> instrumenter =
            WithSpanSingletons.instrumenterWithAttributes();
        Context current = Context.current();
        if (!instrumenter.shouldStart(current, request)) {
          return null;
        }
        Context context = instrumenter.start(current, request);
        return new WithSpanAttributesAdviceScope(method, request, context, context.makeCurrent());
      }

      public Object end(@Nullable Object returnValue, @Nullable Throwable throwable) {
        scope.close();
        AsyncOperationEndSupport<MethodRequest, Object> operationEndSupport =
            AsyncOperationEndSupport.create(
                WithSpanSingletons.instrumenterWithAttributes(),
                Object.class,
                method.getReturnType());
        return operationEndSupport.asyncEnd(context, request, returnValue, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static WithSpanAttributesAdviceScope onEnter(
        @Advice.Origin Method originMethod,
        @Advice.AllArguments(typing = Assigner.Typing.DYNAMIC) Object[] args) {
      // Every usage of @Advice.Origin Method is replaced with a call to Class.getMethod, copy it
      // to advice scope so that there would be only one call to Class.getMethod.
      return WithSpanAttributesAdviceScope.start(originMethod, args);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Object stopSpan(
        @Advice.Return @Nullable Object returnValue,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter WithSpanAttributesAdviceScope adviceScope) {
      if (adviceScope != null) {
        return adviceScope.end(returnValue, throwable);
      }
      return returnValue;
    }
  }
}
