/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.io.IOException;
import javax.annotation.Nullable;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.impl.execchain.ClientExecChain;

final class TracingProtocolExec implements ClientExecChain {

  private static final String REQUEST_CONTEXT_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".context";
  private static final String REQUEST_WRAPPER_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".requestWrapper";
  private static final String REDIRECT_COUNT_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".redirectCount";

  private final Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter;
  private final ContextPropagators propagators;
  private final ClientExecChain exec;

  TracingProtocolExec(
      Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter,
      ContextPropagators propagators,
      ClientExecChain exec) {
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
      ApacheHttpClientRequest instrumenterRequest =
          httpContext.getAttribute(REQUEST_WRAPPER_ATTRIBUTE_ID, ApacheHttpClientRequest.class);
      // Request already had a context so a redirect. Don't create a new span just inject and
      // execute.
      propagators.getTextMapPropagator().inject(context, request, HttpHeaderSetter.INSTANCE);
      return execute(route, request, instrumenterRequest, httpContext, httpExecutionAware, context);
    }

    HttpHost host = null;
    if (route.getTargetHost() != null) {
      host = route.getTargetHost();
    } else if (httpContext.getTargetHost() != null) {
      host = httpContext.getTargetHost();
    }
    if (host != null) {
      if ((host.getSchemeName().equals("https") && host.getPort() == 443)
          || (host.getSchemeName().equals("http") && host.getPort() == 80)) {
        // port seems to be added to the host by route planning for standard ports even if not
        // specified in the URL. There doesn't seem to be a way to differentiate between explicit
        // and implicit port, but ignore in both cases to match the more common case.
        host = new HttpHost(host.getHostName(), -1, host.getSchemeName());
      }
    }
    ApacheHttpClientRequest instrumenterRequest = new ApacheHttpClientRequest(host, request);

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, instrumenterRequest)) {
      return exec.execute(route, request, httpContext, httpExecutionAware);
    }

    context = instrumenter.start(parentContext, instrumenterRequest);
    httpContext.setAttribute(REQUEST_CONTEXT_ATTRIBUTE_ID, context);
    httpContext.setAttribute(REQUEST_WRAPPER_ATTRIBUTE_ID, instrumenterRequest);
    httpContext.setAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, 0);

    propagators.getTextMapPropagator().inject(context, request, HttpHeaderSetter.INSTANCE);

    return execute(route, request, instrumenterRequest, httpContext, httpExecutionAware, context);
  }

  private CloseableHttpResponse execute(
      HttpRoute route,
      HttpRequestWrapper request,
      ApacheHttpClientRequest instrumenterRequest,
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
      if (!pendingRedirect(context, httpContext, request, instrumenterRequest, response)) {
        instrumenter.end(context, instrumenterRequest, response, error);
      }
    }
  }

  private boolean pendingRedirect(
      Context context,
      HttpClientContext httpContext,
      HttpRequestWrapper request,
      ApacheHttpClientRequest instrumenterRequest,
      @Nullable CloseableHttpResponse response) {
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
    try {
      if (!DefaultRedirectStrategy.INSTANCE.isRedirected(request, response, httpContext)) {
        return false;
      }
    } catch (ProtocolException e) {
      // DefaultRedirectStrategy.isRedirected cannot throw this so just return a default.
      return false;
    }

    // Very hacky and a bit slow, but the only way to determine whether the client will fail with
    // a circular redirect, which happens before exec decorators run.
    RedirectLocations redirectLocations =
        (RedirectLocations) httpContext.getAttribute(HttpClientContext.REDIRECT_LOCATIONS);
    if (redirectLocations != null) {
      RedirectLocations copy = new RedirectLocations();
      copy.addAll(redirectLocations);

      try {
        DefaultRedirectStrategy.INSTANCE.getLocationURI(request, response, httpContext);
      } catch (ProtocolException e) {
        // We will not be returning to the Exec, finish the span.
        instrumenter.end(context, instrumenterRequest, response, new ClientProtocolException(e));
        return true;
      } finally {
        httpContext.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, copy);
      }
    }

    int redirectCount = httpContext.getAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, Integer.class);
    if (++redirectCount > httpContext.getRequestConfig().getMaxRedirects()) {
      return false;
    }

    httpContext.setAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, redirectCount);
    return true;
  }
}
