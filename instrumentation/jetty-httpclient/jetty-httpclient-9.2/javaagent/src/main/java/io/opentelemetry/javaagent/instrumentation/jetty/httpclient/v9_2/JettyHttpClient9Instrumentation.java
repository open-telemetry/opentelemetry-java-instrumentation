/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientWrapUtil.wrapResponseListeners;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientTracingListener;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.HttpRequest;
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
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient9Advice {

    @AssignReturned.ToArguments(@ToArgument(value = 1, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Object[] addTracingEnter(
        @Advice.Argument(value = 0) HttpRequest httpRequest,
        @Advice.Argument(1) List<Response.ResponseListener> listeners) {
      Context parentContext = Context.current();
      Context context =
          JettyClientTracingListener.handleRequest(parentContext, httpRequest, instrumenter());
      if (context == null) {
        return new Object[] {null, listeners};
      }

      List<Response.ResponseListener> wrappedListeners =
          wrapResponseListeners(parentContext, listeners);
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
      Context context = (Context) enterResult[0];
      if (context == null) {
        return;
      }

      if (throwable != null) {
        instrumenter().end(context, httpRequest, null, throwable);
      }
    }
  }
}
