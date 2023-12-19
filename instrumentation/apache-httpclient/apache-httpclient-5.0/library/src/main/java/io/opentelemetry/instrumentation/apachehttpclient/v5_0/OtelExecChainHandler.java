/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolException;

public class OtelExecChainHandler implements ExecChainHandler {

  private static final String REQUEST_CONTEXT_ATTRIBUTE_ID =
      OtelExecChainHandler.class.getName() + ".context";
  private static final String REQUEST_WRAPPER_ATTRIBUTE_ID =
      OtelExecChainHandler.class.getName() + ".requestWrapper";
  private static final String REDIRECT_COUNT_ATTRIBUTE_ID =
      OtelExecChainHandler.class.getName() + ".redirectCount";

  private final Instrumenter<ApacheHttpClient5Request, HttpResponse> instrumenter;
  private final ContextPropagators propagators;

  public OtelExecChainHandler(
      Instrumenter<ApacheHttpClient5Request, HttpResponse> instrumenter,
      ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public ClassicHttpResponse execute(ClassicHttpRequest request, Scope scope, ExecChain chain)
      throws IOException, HttpException {
    Context context = scope.clientContext.getAttribute(REQUEST_CONTEXT_ATTRIBUTE_ID, Context.class);
    request.setVersion(scope.clientContext.getProtocolVersion());
    if (context != null) {
      ApacheHttpClient5Request instrumenterRequest =
          scope.clientContext.getAttribute(
              REQUEST_WRAPPER_ATTRIBUTE_ID, ApacheHttpClient5Request.class);
      // Request already had a context so a redirect. Don't create a new span just inject and
      // execute.
      propagators.getTextMapPropagator().inject(context, request, HttpHeaderSetter.INSTANCE);
      return execute(request, instrumenterRequest, scope.clientContext, chain, scope, context);
    }

    ApacheHttpClient5Request instrumenterRequest = getApacheHttpClient5Request(request, scope);

    Context parentContext = Context.current();
    if (!instrumenter.shouldStart(parentContext, instrumenterRequest)) {
      return chain.proceed(request, scope);
    }

    context = instrumenter.start(parentContext, instrumenterRequest);
    scope.clientContext.setAttribute(REQUEST_CONTEXT_ATTRIBUTE_ID, context);
    scope.clientContext.setAttribute(REQUEST_WRAPPER_ATTRIBUTE_ID, instrumenterRequest);
    scope.clientContext.setAttribute(REDIRECT_COUNT_ATTRIBUTE_ID, 0);

    propagators.getTextMapPropagator().inject(context, request, HttpHeaderSetter.INSTANCE);

    return execute(request, instrumenterRequest, scope.clientContext, chain, scope, context);
  }

  private ClassicHttpResponse execute(
      ClassicHttpRequest request,
      ApacheHttpClient5Request instrumenterRequest,
      HttpClientContext httpContext,
      ExecChain chain,
      Scope scope,
      Context context)
      throws IOException, HttpException {
    ClassicHttpResponse response = null;
    Throwable error = null;
    try (io.opentelemetry.context.Scope ignored = context.makeCurrent()) {
      response = chain.proceed(request, scope);
      return response;
    } catch (Exception e) {
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
      HttpRequest request,
      ApacheHttpClient5Request instrumenterRequest,
      @Nullable ClassicHttpResponse response) {
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
      for (URI uri : redirectLocations.getAll()) {
        copy.add(uri);
      }

      try {
        DefaultRedirectStrategy.INSTANCE.getLocationURI(request, response, httpContext);
      } catch (HttpException e) {
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

  private static ApacheHttpClient5Request getApacheHttpClient5Request(
      ClassicHttpRequest request, Scope scope) {
    HttpHost host = null;
    if (scope.route.getTargetHost() != null) {
      host = scope.route.getTargetHost();
    } else if (scope.clientContext.getHttpRoute().getTargetHost() != null) {
      host = scope.clientContext.getHttpRoute().getTargetHost();
    }
    if (host != null
        && ((host.getSchemeName().equals("https") && host.getPort() == 443)
            || (host.getSchemeName().equals("http") && host.getPort() == 80))) {
      // port seems to be added to the host by route planning for standard ports even if not
      // specified in the URL. There doesn't seem to be a way to differentiate between explicit
      // and implicit port, but ignore in both cases to match the more common case.
      host = new HttpHost(host.getSchemeName(), host.getHostName(), -1);
    }

    return new ApacheHttpClient5Request(host, request);
  }
}
