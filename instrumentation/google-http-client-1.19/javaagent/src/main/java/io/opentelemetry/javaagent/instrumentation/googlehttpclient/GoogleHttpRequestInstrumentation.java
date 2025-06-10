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

  public static class AdviceScope {

    private static final VirtualField<HttpRequest, Context> HTTP_REQUEST_CONTEXT =
        VirtualField.find(HttpRequest.class, Context.class);
    private final Context context;
    private final Scope scope;
    private final HttpRequest request;

    public AdviceScope(Context context, Scope scope, HttpRequest request) {
      this.context = context;
      this.scope = scope;
      this.request = request;
    }

    @Nullable
    public static AdviceScope start(HttpRequest request) {
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return null;
      }

      Context context = instrumenter().start(parentContext, request);
      return new AdviceScope(context, context.makeCurrent(), request);
    }

    public void end(HttpResponse response, Throwable throwable) {
      scope.close();
      instrumenter().end(context, request, response, throwable);
    }

    public void endWhenThrown(HttpRequest request, Throwable throwable) {
      scope.close();
      if (throwable != null) {
        instrumenter().end(context, request, null, throwable);
      }
    }

    public static AdviceScope fromVirtualFieldContext(HttpRequest request) {
      Context context = HTTP_REQUEST_CONTEXT.get(request);
      if (context == null) {
        return null;
      }
      return new AdviceScope(context, context.makeCurrent(), request);
    }

    public void storeContextToVirtualField(HttpRequest request) {
      HTTP_REQUEST_CONTEXT.set(request, context);
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.This HttpRequest request) {

      AdviceScope scope = AdviceScope.fromVirtualFieldContext(request);
      if (scope != null) {
        // span was created by GoogleHttpClientAsyncAdvice instrumentation below
        // (executeAsync ends up calling execute from a separate thread)
        // so make it current and end it in method exit
        return scope;
      }

      return AdviceScope.start(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return HttpResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.end(response, throwable);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ExecuteAsyncAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope methodEnter(@Advice.This HttpRequest request) {

      AdviceScope scope = AdviceScope.start(request);
      if (scope != null) {
        scope.storeContextToVirtualField(request);
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This HttpRequest request,
        @Advice.Thrown Throwable throwable,
        @Advice.Enter @Nullable AdviceScope scope) {

      if (scope != null) {
        scope.endWhenThrown(request, throwable);
      }
    }
  }
}
