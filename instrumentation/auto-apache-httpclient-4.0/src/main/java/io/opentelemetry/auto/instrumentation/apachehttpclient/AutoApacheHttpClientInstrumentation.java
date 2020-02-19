package io.opentelemetry.auto.instrumentation.apachehttpclient;

import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.distributedcontext.DistributedContextManager;
import io.opentelemetry.helpers.core.SpanScope;
import io.opentelemetry.helpers.apachehttpclient.ApacheHttpClientSpanDecorator;
import io.opentelemetry.metrics.Meter;
import io.opentelemetry.trace.Tracer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

@AutoService(Instrumenter.class)
public class AutoApacheHttpClientInstrumentation extends Instrumenter.Default {

  public static final Tracer TRACER =
    OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto.apache-httpclient-4.0");
  public static final DistributedContextManager CONTEXT_MANAGER =
    OpenTelemetry.getDistributedContextManager();
  public static final Meter METER =
    OpenTelemetry.getMeterFactory().get("io.opentelemetry.auto.apache-httpclient-4.0");
  public static final ApacheHttpClientSpanDecorator DECORATOR =
    new ApacheHttpClientSpanDecorator(TRACER, CONTEXT_MANAGER, METER);

  public AutoApacheHttpClientInstrumentation() {
    super("httpclient", "apache-httpclient", "apache-http-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return not(isInterface()).and(safeHasSuperType(named("org.apache.http.client.HttpClient")));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      getClass().getName() + "$HelperMethods",
      packageName + ".HttpHeadersInjectAdapter",
      getClass().getName() + "$WrappingStatusSettingResponseHandler",
      "io.opentelemetry.helpers.core.BaseSpanDecorator",
      "io.opentelemetry.helpers.core.ClientSpanDecorator",
      "io.opentelemetry.helpers.core.HttpClientSpanDecorator",
      "io.opentelemetry.helpers.apachehttpclient.ApacheHttpClientSpanDecorator",
      packageName + ".AutoHostAndRequestAsHttpUriRequest",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // There are 8 execute(...) methods.  Depending on the version, they may or may not delegate to
    // eachother. Thus, all methods need to be instrumented.  Because of argument position and type,
    // some methods can share the same advice class.  The call depth tracking ensures only 1 span is
    // created

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(1))
        .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
        .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
        .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(2))
        .and(takesArgument(0, named("org.apache.http.HttpHost")))
        .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named("org.apache.http.HttpHost")))
        .and(takesArgument(1, named("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(3))
        .and(takesArgument(0, named("org.apache.http.HttpHost")))
        .and(takesArgument(1, named("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    transformers.put(
      isMethod()
        .and(named("execute"))
        .and(not(isAbstract()))
        .and(takesArguments(4))
        .and(takesArgument(0, named("org.apache.http.HttpHost")))
        .and(takesArgument(1, named("org.apache.http.HttpRequest")))
        .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
        .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
      AutoApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    return transformers;
  }

  public static class HelperMethods {
    public static SpanScope<HttpUriRequest, HttpResponse> doMethodEnter(final HttpUriRequest request) {
      SpanScope<HttpUriRequest, HttpResponse> spanScope =
        DECORATOR.startSpan("http.request", request, request);
      spanScope.onMessageSent(request);
      return spanScope;
    }

    public static void doMethodExit(
      final SpanScope<HttpUriRequest, HttpResponse> spanScope, final Object result, final Throwable throwable) {
      if (spanScope == null) {
        return;
      }
      try {
        if (result instanceof HttpResponse) {
          spanScope.onMessageReceived(result);
          if (throwable == null) {
            spanScope.onSuccess((HttpResponse) result);
          } else {
            spanScope.onError(throwable, (HttpResponse) result);
          }
        } else if (throwable != null) {
          spanScope.onError(throwable, null);
        }

      } finally {
        spanScope.close();
        CallDepthThreadLocalMap.reset(HttpClient.class);
      }
    }
  }

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScope<HttpUriRequest, HttpResponse> methodEnter(@Advice.Argument(0) final HttpUriRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return HelperMethods.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
      @Advice.Enter final SpanScope<HttpUriRequest, HttpResponse> spanScope,
      @Advice.Return final Object result,
      @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScope, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScope<HttpUriRequest, HttpResponse> methodEnter(
      @Advice.Argument(0) final HttpUriRequest request,
      @Advice.Argument(
        value = 1,
        optional = true,
        typing = Assigner.Typing.DYNAMIC,
        readOnly = false)
        Object handler) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      final SpanScope<HttpUriRequest, HttpResponse> spanScope = HelperMethods.doMethodEnter(request);

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
          new WrappingStatusSettingResponseHandler(
            spanScope, (ResponseHandler) handler);
      }
      return spanScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
      @Advice.Enter final SpanScope<HttpUriRequest, HttpResponse> spanScope,
      @Advice.Return final Object result,
      @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScope, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScope<HttpUriRequest, HttpResponse> methodEnter(
      @Advice.Argument(0) final HttpHost host, @Advice.Argument(1) final HttpRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      if (request instanceof HttpUriRequest) {
        return HelperMethods.doMethodEnter((HttpUriRequest) request);
      } else {
        return HelperMethods.doMethodEnter(new AutoHostAndRequestAsHttpUriRequest(host, request));
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
      @Advice.Enter final SpanScope<HttpUriRequest, HttpResponse> spanScope,
      @Advice.Return final Object result,
      @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScope, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScope<HttpUriRequest, HttpResponse> methodEnter(
      @Advice.Argument(0) final HttpHost host,
      @Advice.Argument(1) final HttpRequest request,
      @Advice.Argument(
        value = 2,
        optional = true,
        typing = Assigner.Typing.DYNAMIC,
        readOnly = false)
        Object handler) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      final SpanScope<HttpUriRequest, HttpResponse> spanScope;

      if (request instanceof HttpUriRequest) {
        spanScope = HelperMethods.doMethodEnter((HttpUriRequest) request);
      } else {
        spanScope =
          HelperMethods.doMethodEnter(new AutoHostAndRequestAsHttpUriRequest(host, request));
      }

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
          new WrappingStatusSettingResponseHandler(
            spanScope, (ResponseHandler) handler);
      }
      return spanScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
      @Advice.Enter final SpanScope<HttpUriRequest, HttpResponse> spanScope,
      @Advice.Return final Object result,
      @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScope, result, throwable);
    }
  }

  public static class WrappingStatusSettingResponseHandler implements ResponseHandler {
    final SpanScope<HttpUriRequest, HttpResponse> spanScope;
    final ResponseHandler handler;

    public WrappingStatusSettingResponseHandler(final SpanScope<HttpUriRequest, HttpResponse> spanScope, final ResponseHandler handler) {
      this.spanScope = spanScope;
      this.handler = handler;
    }

    @Override
    public Object handleResponse(final HttpResponse response)
      throws ClientProtocolException, IOException {
      if (null != spanScope) {
        spanScope.onSuccess(response);
      }
      return handler.handleResponse(response);
    }
  }
}
