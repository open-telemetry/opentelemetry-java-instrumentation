/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.UriBuilder;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import java.util.Collections;
import org.apache.coyote.ActionCode;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.MessageBytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract tracer for all Tomcat versions. This class must not access any methods of fields of
 * Tomcat classes which have the <code>javax.servlet/jakarta.servlet</code> packages in their
 * signature - these must only be accessed by the version-specific subclasses.
 */
public abstract class TomcatTracer extends HttpServerTracer<Request, Response, Request, Request>
    implements TextMapGetter<Request> {

  private static final Logger logger = LoggerFactory.getLogger(TomcatTracer.class);

  public boolean shouldStartSpan(Request request) {
    Context attachedContext = getServerContext(request);
    if (attachedContext == null) {
      return true;
    }
    logger.debug(
        "Unexpected context found before server handler even started: {}", attachedContext);
    return false;
  }

  public Context startServerSpan(Request request) {
    return startSpan(request, request, request, "HTTP " + request.method().toString());
  }

  @Override
  protected Context customizeContext(Context context, Request request) {
    context = ServerSpanNaming.init(context, CONTAINER);
    return AppServerBridge.init(context);
  }

  @Override
  public Context getServerContext(Request storage) {
    Object attribute = storage.getAttribute(CONTEXT_ATTRIBUTE);
    return attribute instanceof Context ? (Context) attribute : null;
  }

  @Override
  protected Integer peerPort(Request connection) {
    connection.action(ActionCode.REQ_REMOTEPORT_ATTRIBUTE, connection);
    return connection.getRemotePort();
  }

  @Override
  protected String peerHostIp(Request connection) {
    connection.action(ActionCode.REQ_HOST_ADDR_ATTRIBUTE, connection);
    return connection.remoteAddr().toString();
  }

  @Override
  protected String flavor(Request connection, Request request) {
    return request.protocol().toString();
  }

  @Override
  protected TextMapGetter<Request> getGetter() {
    return this;
  }

  @Override
  protected String url(Request request) {
    MessageBytes schemeMessageBytes = request.scheme();
    String scheme = schemeMessageBytes.isNull() ? "http" : schemeMessageBytes.toString();
    String host = request.serverName().toString();
    int serverPort = request.getServerPort();
    String path = request.requestURI().toString();
    String query = request.queryString().toString();

    return UriBuilder.uri(scheme, host, serverPort, path, query);
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
