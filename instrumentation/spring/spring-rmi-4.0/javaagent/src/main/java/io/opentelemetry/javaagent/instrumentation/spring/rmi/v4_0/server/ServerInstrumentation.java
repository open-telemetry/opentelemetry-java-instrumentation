/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.server;

import static io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.SpringRmiSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.remoting.rmi.RmiBasedExporter;
import org.springframework.remoting.support.RemoteInvocation;

public class ServerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.remoting.rmi.RmiBasedExporter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("invoke"))
            .and(takesArgument(0, named("org.springframework.remoting.support.RemoteInvocation"))),
        this.getClass().getName() + "$InvokeMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeMethodAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      private final ClassAndMethod request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(
          CallDepth callDepth, ClassAndMethod request, Context context, Scope scope) {
        this.callDepth = callDepth;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope enter(
          CallDepth callDepth, RmiBasedExporter rmiExporter, RemoteInvocation remoteInvocation) {
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
        }

        Context parentContext = THREAD_LOCAL_CONTEXT.getAndResetContext();
        Class<?> serverClass = rmiExporter.getService().getClass();
        String methodName = remoteInvocation.getMethodName();
        ClassAndMethod request = ClassAndMethod.create(serverClass, methodName);

        if (!serverInstrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, request, null, null);
        }

        Context context = serverInstrumenter().start(parentContext, request);
        return new AdviceScope(callDepth, request, context, context.makeCurrent());
      }

      public void exit(@Nullable Throwable throwable) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          return;
        }
        scope.close();
        serverInstrumenter().end(context, request, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.This RmiBasedExporter thisObject, @Advice.Argument(0) RemoteInvocation remoteInv) {
      return AdviceScope.enter(CallDepth.forClass(RmiBasedExporter.class), thisObject, remoteInv);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.exit(throwable);
    }
  }
}
