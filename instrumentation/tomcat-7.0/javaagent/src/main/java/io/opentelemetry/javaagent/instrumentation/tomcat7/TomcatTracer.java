/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat7;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collections;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TomcatTracer extends HttpServerTracer<Request, Response, Request, Request>
    implements TextMapPropagator.Getter<Request> {

  private static final Logger log = LoggerFactory.getLogger(TomcatTracer.class);
  private static final TomcatTracer TRACER = new TomcatTracer();

  public static TomcatTracer tracer() {
    return TRACER;
  }

  public Context startServerSpan(Request request, Method instrumentedMethod) {
    Context context =
        AppServerBridge.init(startSpan(request, request, request, instrumentedMethod));

    // context must be reattached, because it has new attributes compared to the one returned from
    // startSpan().
    attachServerContext(context, request);
    return context;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.tomcat";
  }

  @Override
  public Context getServerContext(Request storage) {
    Object attribute = storage.getAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Override
  protected Integer peerPort(Request connection) {
    return connection.getRemotePort();
  }

  @Override
  protected String peerHostIP(Request connection) {
    return connection.remoteHost().getString();
  }

  @Override
  protected String flavor(Request connection, Request request) {
    return request.protocol().toString();
  }

  @Override
  protected TextMapPropagator.Getter<Request> getGetter() {
    return this;
  }

  @Override
  protected String url(Request request) {
    MessageBytes schemeMB = request.scheme();
    String scheme = schemeMB.isNull() ? "http" : schemeMB.toString();
    String host = request.serverName().toString();
    int serverPort = request.getServerPort();
    String path = request.requestURI().toString();
    String query = request.queryString().toString();

    try {
      return new URI(scheme, null, host, serverPort, path, query, null).toString();
    } catch (Exception e) {
      log.warn(
          "Malformed url? scheme: {}, host: {}, port: {}, path: {}, query: {}",
          scheme,
          host,
          serverPort,
          path,
          query,
          e);
    }
    return null;
  }

  @Override
  protected String method(Request request) {
    return request.method().toString();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.getHeader(name);
  }

  @Override
  protected int responseStatus(Response response) {
    return response.getStatus();
  }

  @Override
  protected void attachServerContext(Context context, Request storage) {
    storage.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  @Override
  public Iterable<String> keys(Request request) {
    return Collections.list(request.getMimeHeaders().names());
  }

  @Override
  public String get(Request request, String key) {
    return request.getHeader(key);
  }
}
