package datadog.trace.instrumentation.googlehttpclient;

import static datadog.trace.instrumentation.googlehttpclient.GoogleHttpClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".GoogleHttpClientDecorator",
      packageName + ".RequestState",
      getClass().getName() + "$GoogleHttpClientAdvice",
      getClass().getName() + "$GoogleHttpClientAsyncAdvice",
      getClass().getName() + "$HeadersInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod().and(isPublic()).and(named("execute")).and(takesArguments(0)),
        GoogleHttpClientAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(isPublic())
            .and(named("executeAsync"))
            .and(takesArguments(1))
            .and(takesArgument(0, (named("java.util.concurrent.Executor")))),
        GoogleHttpClientAsyncAdvice.class.getName());

    return transformers;
  }

  public static class GoogleHttpClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final HttpRequest request) {

      final ContextStore<HttpRequest, RequestState> contextStore =
          InstrumentationContext.get(HttpRequest.class, RequestState.class);

      RequestState state = contextStore.get(request);

      if (state == null) {
        state = new RequestState(GlobalTracer.get().buildSpan("http.request").start());
        contextStore.put(request, state);
      }

      final Span span = state.getSpan();

      try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
        DECORATE.afterStart(span);
        DECORATE.onRequest(span, request);
        GlobalTracer.get()
            .inject(span.context(), Format.Builtin.HTTP_HEADERS, new HeadersInjectAdapter(request));
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

        try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
          DECORATE.onResponse(span, response);
          DECORATE.onError(span, throwable);

          // If HttpRequest.setThrowExceptionOnExecuteError is set to false, there are no exceptions
          // for a failed request.  Thus, check the response code
          if (response != null && !response.isSuccessStatusCode()) {
            Tags.ERROR.set(span, true);
            span.log(singletonMap(Fields.MESSAGE, response.getStatusMessage()));
          }

          DECORATE.beforeFinish(span);
          span.finish();
        }
      }
    }
  }

  public static class GoogleHttpClientAsyncAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(@Advice.This final HttpRequest request) {
      final Span span = GlobalTracer.get().buildSpan("http.request").start();

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

          try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
            DECORATE.onError(span, throwable);

            DECORATE.beforeFinish(span);
            span.finish();
          }
        }
      }
    }
  }

  public static class HeadersInjectAdapter implements TextMap {
    private final HttpRequest request;

    public HeadersInjectAdapter(final HttpRequest request) {
      this.request = request;
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }

    @Override
    public void put(final String key, final String value) {
      request.getHeaders().put(key, value);
    }
  }
}
