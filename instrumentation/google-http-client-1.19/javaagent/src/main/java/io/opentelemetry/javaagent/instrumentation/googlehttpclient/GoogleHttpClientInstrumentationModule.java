/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

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
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
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
    return singletonMap(
        "com.google.api.client.http.HttpRequest", HttpClientOperation.class.getName());
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
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {

      ContextStore<HttpRequest, HttpClientOperation> storage =
          InstrumentationContext.get(HttpRequest.class, HttpClientOperation.class);
      operation = storage.get(request);
      if (operation != null) {
        // this is the synchronous operation inside of an async operation, so make it current
        // and end it in method exit
        scope = operation.makeCurrent();
        return;
      }

      operation = tracer().startOperation(request);
      scope = operation.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return HttpResponse response,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      tracer().endMaybeExceptionally(operation, response, throwable);
    }
  }

  public static class GoogleHttpClientAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.This HttpRequest request,
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {
      operation = tracer().startOperation(request);
      scope = operation.makeCurrent();
      // propagating the context manually here so this instrumentation will work with and without
      // the executors instrumentation
      InstrumentationContext.get(HttpRequest.class, HttpClientOperation.class)
          .put(request, operation);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelOperation") HttpClientOperation operation,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        tracer().endExceptionally(operation, throwable);
      }
    }
  }
}
