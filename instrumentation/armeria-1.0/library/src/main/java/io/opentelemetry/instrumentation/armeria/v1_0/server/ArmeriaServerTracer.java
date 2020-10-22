/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_0.server;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.grpc.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Getter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import io.opentelemetry.trace.Tracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ArmeriaServerTracer
    extends HttpServerTracer<HttpRequest, RequestLog, ServiceRequestContext, Void> {

  ArmeriaServerTracer() {}

  ArmeriaServerTracer(Tracer tracer) {
    super(tracer);
  }

  @Override
  @Nullable
  public Context getServerContext(Void ctx) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria";
  }

  @Override
  @Nullable
  protected Integer peerPort(ServiceRequestContext ctx) {
    SocketAddress socketAddress = ctx.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetAddress = (InetSocketAddress) socketAddress;
      return inetAddress.getPort();
    }
    return null;
  }

  @Override
  @Nullable
  protected String peerHostIP(ServiceRequestContext ctx) {
    SocketAddress socketAddress = ctx.remoteAddress();
    if (socketAddress instanceof InetSocketAddress) {
      InetSocketAddress inetAddress = (InetSocketAddress) socketAddress;
      return inetAddress.getAddress().getHostAddress();
    }
    return null;
  }

  @Override
  protected String flavor(ServiceRequestContext ctx, HttpRequest req) {
    return ctx.sessionProtocol().toString();
  }

  @Override
  protected Getter<HttpRequest> getGetter() {
    return ArmeriaGetter.INSTANCE;
  }

  @Override
  protected String url(HttpRequest req) {
    return req.uri().toString();
  }

  @Override
  protected String method(HttpRequest req) {
    return req.method().name();
  }

  @Override
  @Nullable
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().get(name);
  }

  @Override
  protected int responseStatus(RequestLog httpResponse) {
    return httpResponse.responseHeaders().status().code();
  }

  @Override
  protected void attachServerContext(Context context, Void ctx) {}

  private static class ArmeriaGetter implements Getter<HttpRequest> {

    private static final ArmeriaGetter INSTANCE = new ArmeriaGetter();

    @Override
    @Nullable
    public String get(HttpRequest carrier, String key) {
      return carrier.headers().get(key);
    }
  }
}
