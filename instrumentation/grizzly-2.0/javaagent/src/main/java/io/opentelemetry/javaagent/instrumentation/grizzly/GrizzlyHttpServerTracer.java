/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrizzlyHttpServerTracer
    extends HttpServerTracer<
        HttpRequestPacket, HttpResponsePacket, HttpRequestPacket, FilterChainContext> {

  private static final Logger log = LoggerFactory.getLogger(GrizzlyHttpServerTracer.class);

  private static final GrizzlyHttpServerTracer TRACER = new GrizzlyHttpServerTracer();

  public static GrizzlyHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String method(HttpRequestPacket httpRequest) {
    return httpRequest.getMethod().getMethodString();
  }

  @Override
  protected String requestHeader(HttpRequestPacket httpRequestPacket, String name) {
    return httpRequestPacket.getHeader(name);
  }

  @Override
  protected int responseStatus(HttpResponsePacket httpResponsePacket) {
    return httpResponsePacket.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, FilterChainContext filterChainContext) {
    filterChainContext.getAttributes().setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  public Context getServerContext(FilterChainContext filterChainContext) {
    Object attribute = filterChainContext.getAttributes().getAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Override
  protected String url(HttpRequestPacket httpRequest) {
    try {
      return new URI(
              (httpRequest.isSecure() ? "https://" : "http://")
                  + httpRequest.serverName()
                  + ":"
                  + httpRequest.getServerPort()
                  + httpRequest.getRequestURI()
                  + (httpRequest.getQueryString() != null
                      ? "?" + httpRequest.getQueryString()
                      : ""))
          .toString();
    } catch (URISyntaxException e) {
      log.warn("Failed to construct request URI", e);
      return null;
    }
  }

  @Override
  protected String peerHostIP(HttpRequestPacket httpRequest) {
    return httpRequest.getRemoteAddress();
  }

  @Override
  protected String flavor(HttpRequestPacket connection, HttpRequestPacket request) {
    return connection.getProtocolString();
  }

  @Override
  protected TextMapGetter<HttpRequestPacket> getGetter() {
    return ExtractAdapter.GETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.grizzly-2.0";
  }

  @Override
  protected Integer peerPort(HttpRequestPacket httpRequest) {
    return httpRequest.getRemotePort();
  }
}
