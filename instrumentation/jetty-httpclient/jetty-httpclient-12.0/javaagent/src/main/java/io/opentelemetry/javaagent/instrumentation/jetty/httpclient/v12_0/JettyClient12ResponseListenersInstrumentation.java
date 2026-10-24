/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientWrapUtil.wrapContentSource;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.JETTY_CLIENT_CONTEXT_KEY;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.io.Content;

class JettyClient12ResponseListenersInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.transport.ResponseListeners");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // for response listeners
    transformer.applyAdviceToMethod(
        nameContains("notify")
            .and(isPublic())
            .and(takesArgument(0, named("org.eclipse.jetty.client.Response"))),
        getClass().getName() + "$JettyHttpClient12RespListenersNotifyAdvice");

    // for complete listeners
    transformer.applyAdviceToMethod(
        nameContains("notifyComplete")
            .and(isPublic())
            .and(takesArgument(0, named("org.eclipse.jetty.client.Result"))),
        getClass().getName() + "$JettyHttpClient12CompleteListenersNotifyAdvice");

    transformer.applyAdviceToMethod(
        named("notifyContentSource")
            .and(takesArgument(0, named("org.eclipse.jetty.client.Response$ContentSourceListener")))
            .and(takesArgument(1, named("org.eclipse.jetty.client.Response")))
            .and(takesArgument(2, named("org.eclipse.jetty.io.Content$Source"))),
        getClass().getName() + "$JettyHttpClient12ContentSourceAdvice");
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12RespListenersNotifyAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnterNotify(@Advice.Argument(0) Response response) {

      Context context =
          (Context) response.getRequest().getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitNotify(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12CompleteListenersNotifyAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnterComplete(@Advice.Argument(0) Result result) {

      Context context = (Context) result.getRequest().getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      return context == null ? null : context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitComplete(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12ContentSourceAdvice {

    @ToArguments(@ToArgument(2))
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Content.Source onEnter(
        @Advice.Argument(1) Response response, @Advice.Argument(2) Content.Source contentSource) {
      Context context =
          (Context) response.getRequest().getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      return context != null ? wrapContentSource(contentSource, context) : contentSource;
    }
  }
}
