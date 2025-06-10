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
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

public class MethodInstrumentation implements TypeInstrumentation {
  private final String className;
  private final Set<String> internalMethodNames;
  private final Set<String> serverMethodNames;
  private final Set<String> clientMethodNames;

  private static final Logger logger = Logger.getLogger(MethodInstrumentation.class.getName());

  public MethodInstrumentation(String className, Set<String> methodNames) {
    this.className = className;
    this.internalMethodNames = filterMethodNames(className, methodNames, SpanKind.INTERNAL, true);
    this.serverMethodNames = filterMethodNames(className, methodNames, SpanKind.CLIENT, false);
    this.clientMethodNames = filterMethodNames(className, methodNames, SpanKind.SERVER, false);
  }

  private static Set<String> filterMethodNames(
      String className, Set<String> methodNames, SpanKind kind, boolean isDefault) {
    String suffix = "=" + kind.name();
    Set<String> methods =
        methodNames.stream()
            .filter(
                methodName ->
                    methodName.toUpperCase(Locale.ROOT).endsWith(suffix)
                        || (!methodName.contains("=") && isDefault))
            .map(
                methodName ->
                    methodName.contains("=")
                        ? methodName.substring(0, methodName.indexOf('='))
                        : methodName)
            .collect(Collectors.toSet());
    logMethods(className, methods, kind);
    return methods;
  }

  private static void logMethods(String className, Set<String> methodNames, SpanKind spanKind) {
    if (!logger.isLoggable(Level.FINE) || methodNames.isEmpty()) {
      return;
    }
    logger.log(
        Level.FINE,
        "Tracing class {0} with methods {1} using span kind {2}",
        new Object[] {
          className,
          methodNames.stream()
              .map(name -> className + "." + name)
              .collect(Collectors.joining(", ")),
          spanKind.name()
        });
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
    applyMethodTransformation(transformer, internalMethodNames, "$InternalMethodAdvice");
    applyMethodTransformation(transformer, clientMethodNames, "$ClientMethodAdvice");
    applyMethodTransformation(transformer, serverMethodNames, "$ServerMethodAdvice");
  }

  private static void applyMethodTransformation(
      TypeTransformer transformer, Set<String> methodNames, String methodAdvice) {
    transformer.applyAdviceToMethod(
        namedOneOf(methodNames.toArray(new String[0])).and(isMethod()),
        mapping ->
            mapping.bind(
                MethodReturnType.class,
                (instrumentedType, instrumentedMethod, assigner, argumentHandler, sort) ->
                    Advice.OffsetMapping.Target.ForStackManipulation.of(
                        instrumentedMethod.getReturnType().asErasure())),
        MethodInstrumentation.class.getName() + methodAdvice);
  }

  // custom annotation that represents the return type of the method
  @interface MethodReturnType {}

  @SuppressWarnings("unused")
  public static class InternalMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelMethod") MethodAndType classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod =
          MethodAndType.create(
              ClassAndMethod.create(declaringClass, methodName), SpanKind.INTERNAL);

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

  @SuppressWarnings("unused")
  public static class ClientMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelMethod") MethodAndType classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod =
          MethodAndType.create(ClassAndMethod.create(declaringClass, methodName), SpanKind.CLIENT);

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

  @SuppressWarnings("unused")
  public static class ServerMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelMethod") MethodAndType classAndMethod,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      classAndMethod =
          MethodAndType.create(ClassAndMethod.create(declaringClass, methodName), SpanKind.SERVER);

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
