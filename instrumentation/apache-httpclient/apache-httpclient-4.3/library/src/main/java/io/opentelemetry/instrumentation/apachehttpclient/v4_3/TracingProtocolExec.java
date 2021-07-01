package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.execchain.ClientExecChain;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TracingProtocolExec implements ClientExecChain {

  private static final String REQUEST_CONTEXT_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".context";
  private static final String REQUEST_WRAPPER_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".requestWrapper";
  private static final String REDIRECT_COUNT_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".redirectCount";

  private final Instrumenter<HttpUriRequest, HttpResponse> instrumenter;
  private final ContextPropagators propagators;
  private final ClientExecChain exec;

  TracingProtocolExec(
      Instrumenter<HttpUriRequest, HttpResponse> instrumenter,
      ContextPropagators propagators, ClientExecChain exec) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
    this.exec = exec;
  }

  @Override
  public CloseableHttpResponse execute(
      HttpRoute route,
      HttpRequestWrapper request,
      HttpClientContext httpContext,
      HttpExecutionAware httpExecutionAware)
      throws IOException, HttpException {
    Context context = httpContext.getAttribute(REQUEST_CONTEXT_ATTRIBUTE_ID, Context.class);
    if (context != null) {
      HttpUriRequest requestAsUriRequest =
          httpContext.getAttribute(REQUEST_WRAPPER_ATTRIBUTE_ID, HttpUriRequest.class);
      // Request already had a context so a redirect. Don't create a new span just inject and execute.
      propagators.getTextMapPropagator().inject(context, request, HttpHeaderSetter.INSTANCE);
      return execute(route, request, requestAsUriRequest, httpContext, httpExecutionAware, context);
    }

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, request)) {
      return exec.execute(route, request, httpContext, httpExecutionAware);
    }

    HttpHost host = null;
    if (route.getTargetHost() != null) {
      host = route.getTargetHost();
    } else if (httpContext.getTargetHost() != null) {
      host = httpContext.getTargetHost();
    }
    HttpUriRequest requestAsUriRequest =
        host != null ? new HostAndRequestAsHttpUriRequest(host, request) : request;

    context = instrumenter.start(parentContext, requestAsUriRequest);
    httpContext.setAttribute(REQUEST_CONTEXT_ATTRIBUTE_ID, context);
    httpContext.setAttribute(REQUEST_WRAPPER_ATTRIBUTE_ID, requestAsUriRequest);
    httpContext.setAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, 0);

    return execute(route, request, requestAsUriRequest, httpContext, httpExecutionAware, context);
  }

  private CloseableHttpResponse execute(
      HttpRoute route,
      HttpRequestWrapper request,
      HttpUriRequest requestAsUriRequest,
      HttpClientContext httpContext,
      HttpExecutionAware httpExecutionAware,
      Context context)
      throws IOException, HttpException {
    CloseableHttpResponse response = null;
    Throwable error = null;
    try (Scope ignored = context.makeCurrent()) {
      response = exec.execute(route, request, httpContext, httpExecutionAware);
      return response;
    } catch (Throwable e) {
      error = e;
      throw e;
    } finally {
      if (!pendingRedirect(httpContext, request, response)) {
        instrumenter.end(context, requestAsUriRequest, response, error);
      }
    }
  }

  private static boolean pendingRedirect(
      HttpClientContext httpContext,
      HttpRequestWrapper request,
      @Nullable CloseableHttpResponse response)
      throws ProtocolException {
    if (response == null) {
      return false;
    }
    if (!httpContext.getRequestConfig().isRedirectsEnabled()) {
      return false;
    }
    // TODO(anuraaga): Support redirect strategies other than the default. There is no way to get
    // the user defined redirect strategy without some tricks, but it's very rare to override
    // the strategy, usually it is either on or off as checked above. We can add support for this
    // later if needed.
    if (!DefaultRedirectStrategy.INSTANCE.isRedirected(request, response, httpContext)) {
      return false;
    }
    int redirectCount = httpContext.getAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, Integer.class);
    if (++redirectCount >= httpContext.getRequestConfig().getMaxRedirects()) {
      return false;
    }
    httpContext.setAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, redirectCount);
    return true;
  }
}
