/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.JETTY_CLIENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientTracingListener;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.transport.HttpRequest;

public class JettyHttpClient12Instrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.transport.HttpRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(takesArgument(0, named("org.eclipse.jetty.client.Response$CompleteListener"))),
        JettyHttpClient12Instrumentation.class.getName() + "$JettyHttpClient12SendAdvice");
    // For request listeners
    transformer.applyAdviceToMethod(
        isMethod().and(nameContains("notify")),
        JettyHttpClient12Instrumentation.class.getName() + "$JettyHttpClient12NotifyAdvice");
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterSend(
        @Advice.This HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      // start span
      Context parentContext = Context.current();
      context = JettyClientTracingListener.handleRequest(parentContext, request, instrumenter());
      if (context == null) {
        return;
      }
      // set context for responseListeners
      request.attribute(JETTY_CLIENT_CONTEXT_KEY, parentContext);

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitSend(
        @Advice.This HttpRequest request,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      // not ending span here unless error, span ended in the interceptor
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12NotifyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterNotify(
        @Advice.This HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = (Context) request.getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context == null) {
        return;
      }
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitNotify(
        @Advice.Local("otelContext") Context context, @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
    }
  }
}
