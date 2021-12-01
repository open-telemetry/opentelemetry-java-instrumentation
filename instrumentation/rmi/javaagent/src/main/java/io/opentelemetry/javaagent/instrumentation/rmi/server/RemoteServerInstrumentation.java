/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.rmi.server.RmiServerSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import java.rmi.Remote;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RemoteServerInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("java.rmi.Remote"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(not(isStatic())),
        this.getClass().getName() + "$PublicMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class PublicMethodAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin("#t") Class<?> declaringClass,
        @Advice.Origin("#m") String methodName,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") ClassAndMethod request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(Remote.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      // TODO review and unify with all other SERVER instrumentation
      Context parentContext = THREAD_LOCAL_CONTEXT.getAndResetContext();
      request = ClassAndMethod.create(declaringClass, methodName);
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
        @Advice.Local("otelRequest") ClassAndMethod request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (callDepth.decrementAndGet() > 0) {
        return;
      }
      if (scope == null) {
        return;
      }
      scope.close();
      instrumenter().end(context, request, null, throwable);
    }
  }
}
