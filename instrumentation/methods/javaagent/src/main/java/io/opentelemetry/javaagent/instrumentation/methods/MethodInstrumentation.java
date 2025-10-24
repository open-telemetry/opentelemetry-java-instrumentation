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
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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

    public static class AdviceScope {
      private final MethodAndType classAndMethod;
      private final Context context;
      private final Scope scope;

      public AdviceScope(MethodAndType classAndMethod, Context context, Scope scope) {
        this.classAndMethod = classAndMethod;
        this.context = context;
        this.scope = scope;
      }

      @Nullable
      public static AdviceScope start(
          SpanKind spanKind, Class<?> declaringClass, String methodName) {
        Context parentContext = currentContext();
        MethodAndType methodAndType =
            MethodAndType.create(ClassAndMethod.create(declaringClass, methodName), spanKind);

        if (!instrumenter().shouldStart(parentContext, methodAndType)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, methodAndType);
        return new AdviceScope(methodAndType, context, context.makeCurrent());
      }

      public Object end(
          Class<?> methodReturnType, Object returnValue, @Nullable Throwable throwable) {
        scope.close();
        return AsyncOperationEndSupport.create(instrumenter(), Void.class, methodReturnType)
            .asyncEnd(context, classAndMethod, returnValue, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @MethodSpanKind SpanKind spanKind,
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName) {
      return AdviceScope.start(spanKind, declaringClass, methodName);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Object stopSpan(
        @MethodReturnType Class<?> methodReturnType,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC) Object returnValue,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {

      if (adviceScope != null) {
        return adviceScope.end(methodReturnType, returnValue, throwable);
      }
      return returnValue;
    }
  }
}
