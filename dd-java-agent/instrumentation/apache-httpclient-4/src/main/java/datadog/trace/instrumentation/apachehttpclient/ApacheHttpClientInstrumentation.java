package datadog.trace.instrumentation.apachehttpclient;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.apachehttpclient.ApacheHttpClientDecorator.DECORATE;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

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
import java.util.HashMap;
import java.util.Iterator;
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
      getClass().getName() + "$HttpHeadersInjectAdapter",
      getClass().getName() + "$WrappingStatusSettingResponseHandler",
      "datadog.trace.agent.decorator.BaseDecorator",
      "datadog.trace.agent.decorator.ClientDecorator",
      "datadog.trace.agent.decorator.HttpClientDecorator",
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
        UriRequestAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
        UriRequestAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
        UriRequestWithHandlerAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        UriRequestWithHandlerAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
        RequestAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        RequestAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
        RequestWithHandlerAdvice.class.getName());

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
        RequestWithHandlerAdvice.class.getName());

    return transformers;
  }

  public static class HelperMethods {
    public static Scope doMethodEnter(final HttpUriRequest request) {
      final Tracer tracer = GlobalTracer.get();
      final Scope scope = tracer.buildSpan("http.request").startActive(true);
      final Span span = scope.span();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        tracer.inject(
            span.context(), Format.Builtin.HTTP_HEADERS, new HttpHeadersInjectAdapter(request));
      }
      return scope;
    }

    public static void doMethodExit(
        final Scope scope, final Object result, final Throwable throwable) {
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

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(@Advice.Argument(0) final HttpUriRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return HelperMethods.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
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

      final Scope scope = HelperMethods.doMethodEnter(request);

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler = new WrappingStatusSettingResponseHandler(scope.span(), (ResponseHandler) handler);
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
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
        @Advice.Enter final Scope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(
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

      final Scope scope;

      if (request instanceof HttpUriRequest) {
        scope = HelperMethods.doMethodEnter((HttpUriRequest) request);
      } else {
        scope = HelperMethods.doMethodEnter(new HostAndRequestAsHttpUriRequest(host, request));
      }

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler = new WrappingStatusSettingResponseHandler(scope.span(), (ResponseHandler) handler);
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Scope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
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
