/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springrmi.server;

import static io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.springrmi.SpringRmiSingletons.serverInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.This RmiBasedExporter thisObject,
        @Advice.Argument(0) RemoteInvocation remoteInv,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Local("otelRequest") ClassAndMethod request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      callDepth = CallDepth.forClass(RmiBasedExporter.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      Context parentContext = THREAD_LOCAL_CONTEXT.getAndResetContext();
      Class<?> serverClass = thisObject.getService().getClass();
      String methodName = remoteInv.getMethodName();
      request = ClassAndMethod.create(serverClass, methodName);

      if (!serverInstrumenter().shouldStart(parentContext, request)) {
        return;
      }

      context = serverInstrumenter().start(parentContext, request);
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
      serverInstrumenter().end(context, request, null, throwable);
    }
  }
}
