package io.opentelemetry.auto.instrumentation.apachehttpclient;

import static io.opentelemetry.auto.instrumentation.apachehttpclient.ApacheHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.ApacheHttpClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.apachehttpclient.HttpHeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanScopePair;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
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
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
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
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".ApacheHttpClientDecorator",
      packageName + ".HostAndRequestAsHttpUriRequest",
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
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    return transformers;
  }

  public static class HelperMethods {
    public static SpanScopePair doMethodEnter(final HttpUriRequest request) {
      final Span span = TRACER.spanBuilder("http.request").startSpan();
      final Scope scope = TRACER.withSpan(span);

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        TRACER.getHttpTextFormat().inject(span.getContext(), request, SETTER);
      }
      return new SpanScopePair(span, scope);
    }

    public static void doMethodExit(
        final SpanScopePair spanScopePair, final Object result, final Throwable throwable) {
      if (spanScopePair == null) {
        return;
      }
      try {
        final Span span = spanScopePair.getSpan();

        if (result instanceof HttpResponse) {
          DECORATE.onResponse(span, (HttpResponse) result);
        } // else they probably provided a ResponseHandler.

        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      } finally {
        spanScopePair.getScope().close();
        CallDepthThreadLocalMap.reset(HttpClient.class);
      }
    }
  }

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair methodEnter(@Advice.Argument(0) final HttpUriRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return HelperMethods.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanScopePair spanScopePair,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScopePair, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair methodEnter(
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

      final SpanScopePair spanScopePair = HelperMethods.doMethodEnter(request);

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
            new WrappingStatusSettingResponseHandler(
                spanScopePair.getSpan(), (ResponseHandler) handler);
      }
      return spanScopePair;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanScopePair spanScopePair,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScopePair, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair methodEnter(
        @Advice.Argument(0) final HttpHost host, @Advice.Argument(1) final HttpRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      if (request instanceof HttpUriRequest) {
        return HelperMethods.doMethodEnter((HttpUriRequest) request);
      } else {
        return HelperMethods.doMethodEnter(new HostAndRequestAsHttpUriRequest(host, request));
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanScopePair spanScopePair,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScopePair, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanScopePair methodEnter(
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

      final SpanScopePair spanScopePair;

      if (request instanceof HttpUriRequest) {
        spanScopePair = HelperMethods.doMethodEnter((HttpUriRequest) request);
      } else {
        spanScopePair =
            HelperMethods.doMethodEnter(new HostAndRequestAsHttpUriRequest(host, request));
      }

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
            new WrappingStatusSettingResponseHandler(
                spanScopePair.getSpan(), (ResponseHandler) handler);
      }
      return spanScopePair;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanScopePair spanScopePair,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(spanScopePair, result, throwable);
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
}
