/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.netty.util.AsciiString;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ArmeriaServerTracer
    extends HttpServerTracer<HttpRequest, RequestLog, ServiceRequestContext, Void> {

  ArmeriaServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  @Nullable
  public Context getServerContext(Void ctx) {
    return null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria-1.3";
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
    SessionProtocol protocol = ctx.sessionProtocol();
    if (protocol.isMultiplex()) {
      return "HTTP/2.0";
    } else {
      return "HTTP/1.1";
    }
  }

  @Override
  protected TextMapGetter<HttpRequest> getGetter() {
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

  private static class ArmeriaGetter implements TextMapGetter<HttpRequest> {

    private static final ArmeriaGetter INSTANCE = new ArmeriaGetter();

    @Override
    public Iterable<String> keys(HttpRequest httpRequest) {
      return httpRequest.headers().names().stream()
          .map(AsciiString::toString)
          .collect(Collectors.toList());
    }

    @Override
    @Nullable
    public String get(@Nullable HttpRequest carrier, String key) {
      if (carrier == null) {
        return null;
      }
      return carrier.headers().get(key);
    }
  }
}
