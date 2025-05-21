/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.googlehttpclient.GoogleHttpClientSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GoogleHttpRequestInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // HttpRequest is a final class.  Only need to instrument it exactly
    // Note: the rest of com.google.api is ignored in AdditionalLibraryIgnoresMatcher to speed
    // things up
    return named("com.google.api.client.http.HttpRequest");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("execute")).and(takesArguments(0)),
        this.getClass().getName() + "$ExecuteAdvice");

    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("executeAsync"))
            .and(takesArguments(1))
            .and(takesArgument(0, Executor.class)),
        this.getClass().getName() + "$ExecuteAsyncAdvice");
  }

  public static class AdviceLocals {
    public final Context context;
    public final Scope scope;

    public AdviceLocals(Context context, Scope scope) {
      this.context = context;
      this.scope = scope;
    }

    public static AdviceLocals start(HttpRequest request) {

      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new AdviceLocals(context, context.makeCurrent());
    }

    public void end(HttpRequest request, HttpResponse response, Throwable throwable) {
      scope.close();
      instrumenter().end(context, request, response, throwable);
    }

    public void endWhenThrown(HttpRequest request, Throwable throwable) {
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals methodEnter(@Advice.This HttpRequest request) {
      Context virtualFieldContext =
          VirtualField.find(HttpRequest.class, Context.class).get(request);
      if (virtualFieldContext != null) {
        // span was created by GoogleHttpClientAsyncAdvice instrumentation below
        // (executeAsync ends up calling execute from a separate thread)
        // so make it current and end it in method exit
        return new AdviceLocals(virtualFieldContext, virtualFieldContext.makeCurrent());
      }

      return AdviceLocals.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This HttpRequest request,
        @Advice.Return HttpResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals != null) {
        locals.end(request, response, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAsyncAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceLocals methodEnter(@Advice.This HttpRequest request) {

      AdviceLocals locals = AdviceLocals.start(request);
      if (locals != null) {
        VirtualField.find(HttpRequest.class, Context.class).set(request, locals.context);
      }

      return locals;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This HttpRequest request,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceLocals locals) {

      if (locals != null) {
        locals.endWhenThrown(request, throwable);
      }
    }
  }
}
