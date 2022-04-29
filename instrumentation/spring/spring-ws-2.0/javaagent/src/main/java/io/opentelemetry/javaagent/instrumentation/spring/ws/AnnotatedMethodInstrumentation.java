/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.ws;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.spring.ws.SpringWsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;

public class AnnotatedMethodInstrumentation implements TypeInstrumentation {
  private static final String[] ANNOTATION_CLASSES =
      new String[] {
        "org.springframework.ws.server.endpoint.annotation.PayloadRoot",
        "org.springframework.ws.soap.server.endpoint.annotation.SoapAction",
        "org.springframework.ws.soap.addressing.server.annotation.Action"
      };

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.ws.server.endpoint.annotation.PayloadRoot");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return declaresMethod(isAnnotatedWith(namedOneOf(ANNOTATION_CLASSES)));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isAnnotatedWith(namedOneOf(ANNOTATION_CLASSES))),
        AnnotatedMethodInstrumentation.class.getName() + "$AnnotatedMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class AnnotatedMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void startSpan(
        @Advice.Origin("#t") Class<?> codeClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") SpringWsRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      callDepth = CallDepth.forClass(PayloadRoot.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = currentContext();
      request = SpringWsRequest.create(codeClass, methodName);
      if (!instrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = instrumenter().start(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") SpringWsRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
