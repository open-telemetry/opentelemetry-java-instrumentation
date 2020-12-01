/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext;
import static io.opentelemetry.javaagent.instrumentation.googlehttpclient.GoogleHttpClientTracer.tracer;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GoogleHttpClientInstrumentationModule extends InstrumentationModule {
  public GoogleHttpClientInstrumentationModule() {
    super("google-http-client", "google-http-client-1.19");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.google.api.client.http.HttpRequest", Context.class.getName());
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpRequestInstrumentation());
  }

  public static class HttpRequestInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      // HttpRequest is a final class.  Only need to instrument it exactly
      // Note: the rest of com.google.api is ignored in AdditionalLibraryIgnoresMatcher to speed
      // things up
      return named("com.google.api.client.http.HttpRequest");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          isMethod().and(isPublic()).and(named("execute")).and(takesArguments(0)),
          GoogleHttpClientInstrumentationModule.class.getName() + "$GoogleHttpClientAdvice");

      transformers.put(
          isMethod()
              .and(isPublic())
              .and(named("executeAsync"))
              .and(takesArguments(1))
              .and(takesArgument(0, (named("java.util.concurrent.Executor")))),
          GoogleHttpClientInstrumentationModule.class.getName() + "$GoogleHttpClientAsyncAdvice");

      return transformers;
    }
  }

  public static class GoogleHttpClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      ContextStore<HttpRequest, Context> contextStore =
          InstrumentationContext.get(HttpRequest.class, Context.class);
      context = contextStore.get(request);

      if (context == null) {
        Context parentContext = currentContext();
        if (tracer().shouldStartSpan(parentContext)) {
          context = tracer().startSpan(parentContext, request, request.getHeaders());
          contextStore.put(request, context);
          scope = context.makeCurrent();
        }
      } else {
        // span was created by GoogleHttpClientAsyncAdvice instrumentation below
        scope = context.makeCurrent();
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return HttpResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable == null) {
        tracer().end(context, response);
      } else {
        tracer().endExceptionally(context, response, throwable);
      }
      // If HttpRequest.setThrowExceptionOnExecuteError is set to false, there are no exceptions
      // for a failed request.  Thus, check the response code
      if (response != null && !response.isSuccessStatusCode()) {
        spanFromContext(context).setStatus(StatusCode.ERROR);
      }
    }
  }

  public static class GoogleHttpClientAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This HttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, request, request.getHeaders());

      // propagating the context manually here so this instrumentation will work with and without
      // the executors instrumentation
      ContextStore<HttpRequest, Context> contextStore =
          InstrumentationContext.get(HttpRequest.class, Context.class);
      contextStore.put(request, context);

      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
    }
  }
}
