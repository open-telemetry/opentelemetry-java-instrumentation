/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.methods;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static io.opentelemetry.javaagent.instrumentation.methods.MethodSingletons.getBootstrapLoader;
import static io.opentelemetry.javaagent.instrumentation.methods.MethodSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.any;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.none;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Collection;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodInstrumentation implements TypeInstrumentation {
  private final String className;
  private final Map<SpanKind, Collection<String>> methodNames;

  public MethodInstrumentation(String className, Map<SpanKind, Collection<String>> methodNames) {
    this.className = className;
    this.methodNames = methodNames;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    if (className == null) {
      return any();
    }
    ElementMatcher<ClassLoader> delegate = hasClassesNamed(className);
    return target -> {
      // hasClassesNamed does not support null class loader, so we provide a custom loader that
      // can be used to look up resources in bootstrap loader
      if (target == null) {
        target = getBootstrapLoader();
      }
      return delegate.matches(target);
    };
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return className == null ? none() : hasSuperType(named(className));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    for (Map.Entry<SpanKind, Collection<String>> entry : methodNames.entrySet()) {
      SpanKind spanKind = entry.getKey();
      Collection<String> names = entry.getValue();
      transformer.applyAdviceToMethod(
          namedOneOf(names.toArray(new String[0])).and(isMethod()),
          mapping ->
              mapping
                  .bind(
                      MethodReturnType.class,
                      (instrumentedType, instrumentedMethod, assigner, argumentHandler, sort) ->
                          Advice.OffsetMapping.Target.ForStackManipulation.of(
                              instrumentedMethod.getReturnType().asErasure()))
                  .bind(
                      MethodSpanKind.class,
                      new EnumerationDescription.ForLoadedEnumeration(spanKind)),
          MethodInstrumentation.class.getName() + "$MethodAdvice");
    }
  }

  // custom annotation that represents the return type of the method
  @interface MethodReturnType {}

  // custom annotation that represents the SpanKind of the method
  @interface MethodSpanKind {}

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @MethodSpanKind SpanKind spanKind,
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelMethod") MethodAndType classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod =
          MethodAndType.create(ClassAndMethod.create(declaringClass, methodName), spanKind);

      if (!instrumenter().shouldStart(parentContext, classAndMethod)) {
        return;
      }

      context = instrumenter().start(parentContext, classAndMethod);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @MethodReturnType Class<?> methodReturnType,
        @Advice.Local("otelMethod") MethodAndType classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();

      returnValue =
          AsyncOperationEndSupport.create(instrumenter(), Void.class, methodReturnType)
              .asyncEnd(context, classAndMethod, returnValue, throwable);
    }
  }
}
