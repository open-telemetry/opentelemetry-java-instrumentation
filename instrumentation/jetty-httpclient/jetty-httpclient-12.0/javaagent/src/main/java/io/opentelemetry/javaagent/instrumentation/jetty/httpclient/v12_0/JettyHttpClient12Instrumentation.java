/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientWrapUtil.wrapRequestContent;
import static io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientWrapUtil.wrapRequestTrailers;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.JETTY_CLIENT_CONTEXT_KEY;
import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0.JettyHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.nameContains;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientTracingListener;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.http.HttpFields;

class JettyHttpClient12Instrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.client.transport.HttpRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("send")
            .and(takesArgument(0, named("org.eclipse.jetty.client.Response$CompleteListener"))),
        getClass().getName() + "$JettyHttpClient12SendAdvice");
    // For request listeners
    transformer.applyAdviceToMethod(
        nameContains("notify"), getClass().getName() + "$JettyHttpClient12NotifyAdvice");
    transformer.applyAdviceToMethod(
        named("body").and(takesArgument(0, named("org.eclipse.jetty.client.Request$Content"))),
        getClass().getName() + "$JettyHttpClient12BodyAdvice");
    transformer.applyAdviceToMethod(
        named("trailersSupplier").and(takesArgument(0, named("java.util.function.Supplier"))),
        getClass().getName() + "$JettyHttpClient12TrailersAdvice");
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12SendAdvice {

    public static class AdviceLocals {
      public final Context context;
      public final Scope scope;

      public AdviceLocals(Context context, Scope scope) {
        this.context = context;
        this.scope = scope;
      }
    }

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static AdviceLocals onEnterSend(@Advice.This HttpRequest request) {
      Context parentContext = Context.current();
      request.attribute(JETTY_CLIENT_CONTEXT_KEY, parentContext);
      Request.Content body = request.getBody();
      if (body != null) {
        request.body(wrapRequestContent(body, parentContext));
      }
      Supplier<HttpFields> trailersSupplier = request.getTrailersSupplier();
      if (trailersSupplier != null) {
        request.trailersSupplier(wrapRequestTrailers(trailersSupplier, parentContext));
      }
      Context context =
          JettyClientTracingListener.handleRequest(parentContext, request, instrumenter());
      if (context == null) {
        return null;
      }

      return new AdviceLocals(context, context.makeCurrent());
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitSend(
        @Advice.This HttpRequest request,
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals == null) {
        return;
      }

      // not ending span here unless error, span ended in the interceptor
      locals.scope.close();
      if (throwable != null) {
        instrumenter().end(locals.context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12BodyAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(0))
    public static Request.Content onEnterBody(
        @Advice.This HttpRequest request, @Advice.Argument(0) Request.Content content) {
      Context context = (Context) request.getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context != null && content != null) {
        return wrapRequestContent(content, context);
      }
      return content;
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12TrailersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Advice.AssignReturned.ToArguments(@Advice.AssignReturned.ToArguments.ToArgument(0))
    public static Supplier<HttpFields> onEnterTrailers(
        @Advice.This HttpRequest request,
        @Advice.Argument(0) Supplier<HttpFields> trailersSupplier) {
      Context context = (Context) request.getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context != null && trailersSupplier != null) {
        return wrapRequestTrailers(trailersSupplier, context);
      }
      return trailersSupplier;
    }
  }

  @SuppressWarnings("unused")
  public static class JettyHttpClient12NotifyAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnterNotify(@Advice.This HttpRequest request) {
      Context context = (Context) request.getAttributes().get(JETTY_CLIENT_CONTEXT_KEY);
      if (context == null) {
        return null;
      }
      return context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class, inline = false)
    public static void onExitNotify(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
