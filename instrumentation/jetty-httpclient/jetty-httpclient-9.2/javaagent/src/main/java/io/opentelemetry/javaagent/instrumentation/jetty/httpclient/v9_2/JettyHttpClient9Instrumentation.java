/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientWrapUtil.wrapResponseListeners;
import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyHttpClient9TracingInterceptor;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClient9Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.HttpClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("send"))
            .and(takesArgument(0, named("org.eclipse.jetty.client.HttpRequest")))
            .and(takesArgument(1, List.class)),
        JettyHttpClient9Instrumentation.class.getName() + "$JettyHttpClient9Advice");
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient9Advice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void addTracingEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(value = 1, readOnly = false) List<Response.ResponseListener> listeners,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, httpRequest)) {
        return;
      }

      // First step is to attach the tracer to the Jetty request. Request listeners are wrapped here
      JettyHttpClient9TracingInterceptor requestInterceptor =
          new JettyHttpClient9TracingInterceptor(parentContext, instrumenter());
      requestInterceptor.attachToRequest(httpRequest);

      // Second step is to wrap all the important result callback
      listeners = wrapResponseListeners(parentContext, listeners);

      context = requestInterceptor.getContext();
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void exitTracingInterceptor(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      // not ending span here unless error, span ended in the interceptor
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, httpRequest, null, throwable);
      }
    }
  }
}
