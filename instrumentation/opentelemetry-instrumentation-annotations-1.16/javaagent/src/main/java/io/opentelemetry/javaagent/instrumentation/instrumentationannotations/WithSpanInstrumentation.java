/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationannotations;

import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.AnnotationSingletons.instrumenter;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.AnnotationSingletons.instrumenterWithAttributes;
import static io.opentelemetry.javaagent.instrumentation.instrumentationannotations.KotlinCoroutineUtil.isKotlinSuspendMethod;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.hasParameters;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.whereAny;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

class WithSpanInstrumentation implements TypeInstrumentation {

  private final ElementMatcher.Junction<AnnotationSource> annotatedMethodMatcher;
  private final ElementMatcher.Junction<MethodDescription> annotatedParametersMatcher;
  // this matcher matches all methods that should be excluded from transformation
  private final ElementMatcher.Junction<MethodDescription> excludedMethodsMatcher;

  WithSpanInstrumentation() {
    annotatedMethodMatcher =
        isAnnotatedWith(named("application.io.opentelemetry.instrumentation.annotations.WithSpan"));
    annotatedParametersMatcher =
        hasParameters(
            whereAny(
                isAnnotatedWith(
                    named(
                        "application.io.opentelemetry.instrumentation.annotations.SpanAttribute"))));
    // exclude all kotlin suspend methods, these are handle in kotlinx-coroutines instrumentation
    excludedMethodsMatcher =
        AnnotationExcludedMethods.configureExcludedMethods().or(isKotlinSuspendMethod());
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
        Instrumenter<Method, Object> instrumenter = instrumenter();
        Context current = AnnotationSingletons.getContextForMethod(method);
        if (!instrumenter.shouldStart(current, method)) {
          return null;
        }
        Context context = instrumenter.start(current, method);
        return new WithSpanAdviceScope(method, context, context.makeCurrent());
      }

      public Object end(@Nullable Object returnValue, @Nullable Throwable throwable) {
        scope.close();
        AsyncOperationEndSupport<Method, Object> operationEndSupport =
            AsyncOperationEndSupport.create(instrumenter(), Object.class, method.getReturnType());
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
        Instrumenter<MethodRequest, Object> instrumenter = instrumenterWithAttributes();
        Context current = AnnotationSingletons.getContextForMethod(method);
        MethodRequest request = new MethodRequest(method, args);
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
                instrumenterWithAttributes(), Object.class, method.getReturnType());
        return operationEndSupport.asyncEnd(context, request, returnValue, throwable);
      }
    }

    @Nullable
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
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) @Nullable Object returnValue,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter WithSpanAttributesAdviceScope adviceScope) {
      if (adviceScope != null) {
        return adviceScope.end(returnValue, throwable);
      }
      return returnValue;
    }
  }
}
