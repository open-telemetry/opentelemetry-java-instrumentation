package datadog.trace.instrumentation.apachehttpclient;

import static datadog.trace.agent.tooling.ByteBuddyElementMatchers.safeHasSuperType;
import static datadog.trace.instrumentation.apachehttpclient.ApacheHttpClientDecorator.DECORATE;
import static datadog.trace.instrumentation.apachehttpclient.HttpHeadersInjectAdapter.SETTER;
import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
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
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
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
    public static AgentScope doMethodEnter(final HttpUriRequest request) {
      final AgentSpan span = startSpan("http.request");
      final AgentScope scope = activateSpan(span, true);

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      final boolean awsClientCall = request.getHeaders("amz-sdk-invocation-id").length > 0;
      // AWS calls are often signed, so we can't add headers without breaking the signature.
      if (!awsClientCall) {
        propagate().inject(span, request, SETTER);
      }
      return scope;
    }

    public static void doMethodExit(
        final AgentScope scope, final Object result, final Throwable throwable) {
      if (scope == null) {
        return;
      }
      try {
        final AgentSpan span = scope.span();

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

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(@Advice.Argument(0) final HttpUriRequest request) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return HelperMethods.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentScope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(
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

      final AgentScope scope = HelperMethods.doMethodEnter(request);

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler = new WrappingStatusSettingResponseHandler(scope.span(), (ResponseHandler) handler);
      }
      return scope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final AgentScope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(
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
        @Advice.Enter final AgentScope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope methodEnter(
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

      final AgentScope scope;

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
        @Advice.Enter final AgentScope scope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {

      HelperMethods.doMethodExit(scope, result, throwable);
    }
  }

  public static class WrappingStatusSettingResponseHandler implements ResponseHandler {
    final AgentSpan span;
    final ResponseHandler handler;

    public WrappingStatusSettingResponseHandler(
        final AgentSpan span, final ResponseHandler handler) {
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
