/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.googlehttpclient;

import static io.opentelemetry.auto.instrumentation.googlehttpclient.GoogleHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.googlehttpclient.GoogleHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.googlehttpclient.HeadersInjectAdapter.SETTER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class GoogleHttpClientInstrumentation extends Instrumenter.Default {
  public GoogleHttpClientInstrumentation() {
    super("google-http-client");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // HttpRequest is a final class.  Only need to instrument it exactly
    // Note: the rest of com.google.api is ignored in AdditionalLibraryIgnoresMatcher to speed
    // things up
    return named("com.google.api.client.http.HttpRequest");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.google.api.client.http.HttpRequest", RequestState.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GoogleHttpClientDecorator",
      packageName + ".RequestState",
      packageName + ".HeadersInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(named("execute")).and(takesArguments(0)),
        GoogleHttpClientInstrumentation.class.getName() + "$GoogleHttpClientAdvice");

    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("executeAsync"))
            .and(takesArguments(1))
            .and(takesArgument(0, (named("java.util.concurrent.Executor")))),
        GoogleHttpClientInstrumentation.class.getName() + "$GoogleHttpClientAsyncAdvice");

    return transformers;
  }

  public static class GoogleHttpClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final HttpRequest request) {

      ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);

      RequestState state = contextStore.get(request);

      if (state == null) {
        state =
            new RequestState(
                TRACER
                    .spanBuilder(DECORATE.spanNameForRequest(request))
                    .setSpanKind(CLIENT)
                    .startSpan());
        contextStore.put(request, state);
      }

      Span span = state.getSpan();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      Context context = withSpan(span, Context.current());
      OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, SETTER);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This final HttpRequest request,
        @Advice.Return final HttpResponse response,
        @Advice.Thrown final Throwable throwable) {

      ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);
      RequestState state = contextStore.get(request);

      if (state != null) {
        Span span = state.getSpan();

        try (Scope scope = currentContextWith(span)) {
          DECORATE.onResponse(span, response);
          DECORATE.onError(span, throwable);

          // If HttpRequest.setThrowExceptionOnExecuteError is set to false, there are no exceptions
          // for a failed request.  Thus, check the response code
          if (response != null && !response.isSuccessStatusCode()) {
            span.setStatus(Status.UNKNOWN);
          }

          DECORATE.beforeFinish(span);
          span.end();
        }
      }
    }
  }

  public static class GoogleHttpClientAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final HttpRequest request) {
      Span span =
          TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(CLIENT).startSpan();

      ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);

      RequestState state = new RequestState(span);
      contextStore.put(request, state);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This final HttpRequest request, @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {

        ContextStore<HttpRequest, RequestState> contextStore =
            InstrumentationContext.get(HttpRequest.class, RequestState.class);
        RequestState state = contextStore.get(request);

        if (state != null) {
          Span span = state.getSpan();

          try (Scope scope = currentContextWith(span)) {
            DECORATE.onError(span, throwable);

            DECORATE.beforeFinish(span);
            span.end();
          }
        }
      }
    }
  }
}
