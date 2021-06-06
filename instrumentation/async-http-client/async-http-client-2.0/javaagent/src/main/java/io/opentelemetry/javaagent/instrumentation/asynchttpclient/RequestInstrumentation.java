/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.asynchttpclient.AsyncHttpClientTracer.tracer;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Pair;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.Request;

public class RequestInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.asynchttpclient.AsyncHttpClient"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("executeRequest")
            .and(takesArgument(0, named("org.asynchttpclient.Request")))
            .and(takesArgument(1, named("org.asynchttpclient.AsyncHandler")))
            .and(isPublic()),
        this.getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Request request,
        @Advice.Argument(1) AsyncHandler<?> handler,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      Context context = tracer().startSpan(parentContext, request, request);
      InstrumentationContext.get(AsyncHandler.class, Pair.class)
          .put(handler, Pair.of(parentContext, context));
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(@Advice.Local("otelScope") Scope scope) {
      if (scope != null) {
        scope.close();
      }
      // span ended in ResponseAdvice or ResponseFailureAdvice
    }
  }
}
