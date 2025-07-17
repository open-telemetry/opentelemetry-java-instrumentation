/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.instrumentation.spring.rmi.v4_0.SpringRmiSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.lang.reflect.Method;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.aopalliance.intercept.MethodInvocation;

public class ClientInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.remoting.rmi.RmiClientInterceptor")
        .or(extendsClass(named("org.springframework.ejb.access.AbstractSlsbInvokerInterceptor")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("invoke"))
            .and(takesArgument(0, named("org.aopalliance.intercept.MethodInvocation"))),
        this.getClass().getName() + "$InvokeMethodAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeMethodAdvice {

    public static class AdviceScope {
      private final Method method;
      private final Context context;
      private final Scope scope;

      public AdviceScope(Method method, Context context, Scope scope) {
        this.method = method;
        this.context = context;
        this.scope = scope;
      }

      public void exit(@Nullable Throwable throwable) {
        scope.close();
        clientInstrumenter().end(context, method, null, throwable);
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.Argument(0) MethodInvocation methodInv) {
      Method method = methodInv.getMethod();
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!clientInstrumenter().shouldStart(parentContext, method)) {
        return null;
      }
      Context context = clientInstrumenter().start(parentContext, method);
      return new AdviceScope(method, context, context.makeCurrent());
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(throwable);
      }
    }
  }
}
