/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientWrapUtil.wrapResponseListeners;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.net.URI;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

class JettyHttpClient9Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.HttpClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("org.eclipse.jetty.client.HttpRequest")))
            .and(takesArgument(1, List.class)),
        getClass().getName() + "$JettyHttpClient9Advice");
    transformer.applyAdviceToMethod(
        named("copyRequest")
            .and(takesArgument(0, named("org.eclipse.jetty.client.HttpRequest")))
            .and(takesArgument(1, URI.class)),
        getClass().getName() + "$CopyAdvice");
  }

  @SuppressWarnings("unused")
  public static class CopyAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) HttpRequest original, @Advice.Return Request copy) {
      JettyRequestContext.copy(original, copy);
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient9Advice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] addTracingEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(1) List<Response.ResponseListener> listeners) {
      Context parentContext = Context.current();
      JettyRequestContext context = JettyRequestContext.start(httpRequest, parentContext);
      if (context == null) {
        return new Object[] {null, listeners};
      }

      List<Response.ResponseListener> wrappedListeners =
          wrapResponseListeners(context.caller, listeners);
      // Completion listeners may immediately resend the request, including on another thread.
      wrappedListeners.add(
          0,
          (Response.CompleteListener)
              result -> {
                if (JettyRequestContext.isProtocolResponse(result.getResponse())) {
                  context.deferCompletion();
                } else {
                  context.complete(httpRequest);
                  JettyRequestContext.completeContinuation(result.getRequest());
                }
              });
      return new Object[] {context, wrappedListeners};
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void exitTracingInterceptor(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable Object[] enterResult) {
      if (enterResult == null) {
        return;
      }
      JettyRequestContext context = (JettyRequestContext) enterResult[0];
      if (context != null && throwable != null) {
        context.end(httpRequest, null, throwable);
        context.complete(httpRequest);
      }
    }
  }
}
