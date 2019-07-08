package datadog.trace.instrumentation.googlehttpclient;

import static datadog.trace.instrumentation.googlehttpclient.GoogleHttpClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.log.Fields;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import java.util.Iterator;
import java.util.Map;

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
  public String[] helperClassNames() {
    return new String[] {
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".GoogleHttpClientClientDecorator",
      getClass().getName() + "$GoogleHttpClientAdvice",
      getClass().getName() + "$HeadersInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(named("execute").and(takesArguments(0))),
        GoogleHttpClientAdvice.class.getName());
  }

  public static class GoogleHttpClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(@Advice.This final HttpRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpRequest.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span = GlobalTracer.get().buildSpan("http.request").start();
      final Scope scope = GlobalTracer.get().scopeManager().activate(span, false);
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      GlobalTracer.get()
          .inject(span.context(), Format.Builtin.HTTP_HEADERS, new HeadersInjectAdapter(request));
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final HttpResponse response,
        @Advice.Thrown final Throwable throwable) {

      if (scope != null) {
        try {
          final Span span = scope.span();
          DECORATE.onResponse(span, response);

          // If HttpRequest.setThrowExceptionOnExecuteError is set to false, there are no exceptions
          // for a failed request.  Thus, check the response code
          if (!response.isSuccessStatusCode()) {
            Tags.ERROR.set(span, true);
            span.log(singletonMap(Fields.MESSAGE, response.getStatusMessage()));
          }
          DECORATE.onError(span, throwable);

          DECORATE.beforeFinish(span);
        } finally {
          scope.close();
          CallDepthThreadLocalMap.reset(HttpRequest.class);
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
