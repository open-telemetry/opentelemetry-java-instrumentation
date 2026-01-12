/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.v2_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.jaxws.v2_0.JaxWsSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.jaxws.common.JaxWsRequest;
import javax.annotation.Nullable;
import javax.xml.ws.Provider;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class WebServiceProviderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("javax.xml.ws.Provider");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.xml.ws.Provider"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("invoke")).and(takesArguments(1)),
        WebServiceProviderInstrumentation.class.getName() + "$InvokeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InvokeAdvice {

    public static class AdviceScope {
      private final CallDepth callDepth;
      private final JaxWsRequest request;
      private final Context context;
      private final Scope scope;

      private AdviceScope(CallDepth callDepth, JaxWsRequest request, Context context, Scope scope) {
        this.callDepth = callDepth;
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(CallDepth callDepth, Object target, String methodName) {
        if (callDepth.getAndIncrement() > 0) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context parentContext = currentContext();
        JaxWsRequest request = new JaxWsRequest(target.getClass(), methodName);
        if (!instrumenter().shouldStart(parentContext, request)) {
          return new AdviceScope(callDepth, null, null, null);
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(callDepth, request, context, context.makeCurrent());
      }

      public void end(Throwable throwable) {
        if (callDepth.decrementAndGet() > 0 || scope == null) {
          return;
        }
        scope.close();
        instrumenter().end(context, request, null, throwable);
      }
    }

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope startSpan(
        @Advice.This Object target, @Advice.Origin("#m") String methodName) {
      CallDepth callDepth = CallDepth.forClass(Provider.class);
      return AdviceScope.start(callDepth, target, methodName);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter AdviceScope adviceScope) {
      adviceScope.end(throwable);
    }
  }
}
