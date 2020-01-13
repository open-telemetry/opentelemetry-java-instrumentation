package io.opentelemetry.auto.instrumentation.googlehttpclient;

import static io.opentelemetry.auto.instrumentation.googlehttpclient.GoogleHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.googlehttpclient.GoogleHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.googlehttpclient.HeadersInjectAdapter.SETTER;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.auto.service.AutoService;
import io.opentelemetry.auto.api.MoreTags;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.util.Collections;
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
    return named("com.google.api.client.http.HttpRequest");
  }

  @Override
  public Map<String, String> contextStore() {
    return Collections.singletonMap(
        "com.google.api.client.http.HttpRequest", RequestState.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".GoogleHttpClientDecorator",
      packageName + ".RequestState",
      getClass().getName() + "$GoogleHttpClientAdvice",
      getClass().getName() + "$GoogleHttpClientAsyncAdvice",
      packageName + ".HeadersInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
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

      final ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);

      RequestState state = contextStore.get(request);

      if (state == null) {
        state = new RequestState(TRACER.spanBuilder("http.request").startSpan());
        contextStore.put(request, state);
      }

      final Span span = state.getSpan();

      try (final Scope scope = TRACER.withSpan(span)) {
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, request);
        TRACER.getHttpTextFormat().inject(span.getContext(), request, SETTER);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This final HttpRequest request,
        @Advice.Return final HttpResponse response,
        @Advice.Thrown final Throwable throwable) {

      final ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);
      final RequestState state = contextStore.get(request);

      if (state != null) {
        final Span span = state.getSpan();

        try (final Scope scope = TRACER.withSpan(span)) {
          DECORATE.onResponse(span, response);
          DECORATE.onError(span, throwable);

          // If HttpRequest.setThrowExceptionOnExecuteError is set to false, there are no exceptions
          // for a failed request.  Thus, check the response code
          if (response != null && !response.isSuccessStatusCode()) {
            span.setStatus(Status.UNKNOWN);
            span.setAttribute(MoreTags.ERROR_MSG, response.getStatusMessage());
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
      final Span span = TRACER.spanBuilder("http.request").startSpan();

      final ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);

      final RequestState state = new RequestState(span);
      contextStore.put(request, state);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.This final HttpRequest request, @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {

        final ContextStore<HttpRequest, RequestState> contextStore =
            InstrumentationContext.get(HttpRequest.class, RequestState.class);
        final RequestState state = contextStore.get(request);

        if (state != null) {
          final Span span = state.getSpan();

          try (final Scope scope = TRACER.withSpan(span)) {
            DECORATE.onError(span, throwable);

            DECORATE.beforeFinish(span);
            span.end();
          }
        }
      }
    }
  }
}
