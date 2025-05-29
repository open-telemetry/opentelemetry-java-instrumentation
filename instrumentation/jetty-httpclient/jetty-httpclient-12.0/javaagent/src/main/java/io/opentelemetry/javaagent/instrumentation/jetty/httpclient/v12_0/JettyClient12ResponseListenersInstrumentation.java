/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.JETTY_CLIENT_CONTEXT_KEY;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;

public class JettyClient12ResponseListenersInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.transport.ResponseListeners");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // for response listeners
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                nameContains("notify")
                    .and(isPublic())
                    .and(takesArgument(0, named("org.eclipse.jetty.client.Response")))),
        JettyClient12ResponseListenersInstrumentation.class.getName()
            + "$JettyHttpClient12RespListenersNotifyAdvice");

    // for complete listeners
    transformer.applyAdviceToMethod(
        isMethod()
            .and(
                nameContains("notifyComplete")
                    .and(isPublic())
                    .and(takesArgument(0, named("org.eclipse.jetty.client.Result")))),
        JettyClient12ResponseListenersInstrumentation.class.getName()
            + "$JettyHttpClient12CompleteListenersNotifyAdvice");
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12RespListenersNotifyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterNotify(
        @Advice.Argument(0) Response response,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = (Context) response.getRequest().getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitNotify(
        @Advice.Argument(0) Response response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12CompleteListenersNotifyAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnterComplete(
        @Advice.Argument(0) Result result,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = (Context) result.getRequest().getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context != null) {
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExitComplete(
        @Advice.Argument(0) Result result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
    }
  }
}
