package datadog.trace.instrumentation.apachehttpclient;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.apachehttpclient.ApacheHttpClientDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.CallDepthThreadLocalMap;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.util.GlobalTracer;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

@AutoService(Instrumenter.class)
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
    super("httpclient", "apache-httpclient", "apache-http-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return safeHasSuperType(named("org.apache.http.client.HttpClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$HttpHeadersInjectAdapter",
      getClass().getName() + "$WrappingStatusSettingResponseHandler",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
      packageName + ".ApacheHttpClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(not(isAbstract()))
            .and(named("execute"))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest"))),
        ClientAdvice.class.getName());
  }

  public static class ClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
        @Advice.Argument(0) final HttpUriRequest request,
        // ResponseHandler could be either slot, but not both.
        @Advice.Argument(
                value = 1,
                optional = true,
                typing = Assigner.Typing.DYNAMIC,
                readOnly = false)
            Object handler1,
        @Advice.Argument(
                value = 2,
                optional = true,
                typing = Assigner.Typing.DYNAMIC,
                readOnly = false)
            Object handler2) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }
      final Tracer tracer = GlobalTracer.get();
      final Scope scope = tracer.buildSpan("http.request").startActive(true);
      final Span span = scope.span();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      // Wrap the handler so we capture the status code
      if (handler1 instanceof ResponseHandler) {
        handler1 = new WrappingStatusSettingResponseHandler(span, (ResponseHandler) handler1);
      } else if (handler2 instanceof ResponseHandler) {
        handler2 = new WrappingStatusSettingResponseHandler(span, (ResponseHandler) handler2);
      }

      final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        tracer.inject(
            span.context(), Format.Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      if (scope != null) {
        try {
          final Span span = scope.span();

          if (result instanceof HttpResponse) {
            DECORATE.onResponse(span, (HttpResponse) result);
          } // else they probably provided a ResponseHandler.

          DECORATE.onError(span, throwable);
          DECORATE.beforeFinish(span);
        } finally {
          scope.close();
          CallDepthThreadLocalMap.reset(HttpClient.class);
        }
      }
    }
  }

  public static class WrappingStatusSettingResponseHandler implements ResponseHandler {
    final Span span;
    final ResponseHandler handler;

    public WrappingStatusSettingResponseHandler(final Span span, final ResponseHandler handler) {
      this.span = span;
      this.handler = handler;
    }

    @Override
    public Object handleResponse(final HttpResponse response)
        throws ClientProtocolException, IOException {
      if (null != span) {
        DECORATE.onResponse(span, response);
      }
      return handler.handleResponse(response);
    }
  }

  public static class HttpHeadersInjectAdapter implements TextMap {

    private final HttpRequest httpRequest;

    public HttpHeadersInjectAdapter(final HttpRequest httpRequest) {
      this.httpRequest = httpRequest;
    }

    @Override
    public void put(final String key, final String value) {
      httpRequest.addHeader(key, value);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
      throw new UnsupportedOperationException(
          "This class should be used only with tracer#inject()");
    }
  }
}
