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
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import java.util.Set;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodInstrumentation implements TypeInstrumentation {
  private final String className;
  private final Set<String> methodNames;

  public MethodInstrumentation(String className, Set<String> methodNames) {
    this.className = className;
    this.methodNames = methodNames;
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
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
    return hasSuperType(named(className));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf(methodNames.toArray(new String[0])),
        MethodInstrumentation.class.getName() + "$MethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class MethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod = ClassAndMethod.create(declaringClass, methodName);
      if (!instrumenter().shouldStart(parentContext, classAndMethod)) {
        return;
      }

      context = instrumenter().start(parentContext, classAndMethod);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Origin Method method,
        @Advice.Local("otelMethod") ClassAndMethod classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
        @Advice.Thrown Throwable throwable) {
      if (scope == null) {
        return;
      }
      scope.close();

      returnValue =
          AsyncOperationEndSupport.create(instrumenter(), Void.class, method.getReturnType())
              .asyncEnd(context, classAndMethod, returnValue, throwable);
    }
  }
}
