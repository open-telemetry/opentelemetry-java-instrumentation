/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.springrmi.client;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.springrmi.SpringRmiSingletons.clientInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.aopalliance.intercept.MethodInvocation;

public class ClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.springframework.remoting.rmi.RmiClientInterceptor");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("org.springframework.remoting.rmi.RmiClientInterceptor"));
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) MethodInvocation methodInv,
        @Advice.Local("method") Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      method = methodInv.getMethod();
      Context parentContext = Java8BytecodeBridge.currentContext();
      if (!clientInstrumenter().shouldStart(parentContext, method)) {
        return;
      }

      context = clientInstrumenter().start(parentContext, method);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Local("method") Method method,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }
      scope.close();
      clientInstrumenter().end(context, method, null, throwable);
    }
  }
}
