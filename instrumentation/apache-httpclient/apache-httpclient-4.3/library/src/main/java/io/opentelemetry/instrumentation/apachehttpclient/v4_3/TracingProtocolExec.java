/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpExecutionAware;
import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.execchain.ClientExecChain;

final class TracingProtocolExec implements ClientExecChain {

  private static final String REQUEST_PARENT_CONTEXT_ATTRIBUTE_ID =
      TracingProtocolExec.class.getName() + ".context";

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

    Context parentContext =
        httpContext.getAttribute(REQUEST_PARENT_CONTEXT_ATTRIBUTE_ID, Context.class);
    if (parentContext == null) {
      parentContext = HttpClientRequestResendCount.initialize(Context.current());
      httpContext.setAttribute(REQUEST_PARENT_CONTEXT_ATTRIBUTE_ID, parentContext);
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

    if (!instrumenter.shouldStart(parentContext, instrumenterRequest)) {
      return exec.execute(route, request, httpContext, httpExecutionAware);
    }

    Context context = instrumenter.start(parentContext, instrumenterRequest);
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
    CloseableHttpResponse response;
    try (Scope ignored = context.makeCurrent()) {
      response = exec.execute(route, request, httpContext, httpExecutionAware);
    } catch (Throwable t) {
      instrumenter.end(context, instrumenterRequest, null, t);
      throw t;
    }
    instrumenter.end(context, instrumenterRequest, response, null);
    return response;
  }
}
