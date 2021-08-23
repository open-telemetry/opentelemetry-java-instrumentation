/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restlet.data.Request;
import org.restlet.data.Response;

public class RestletHttpServerTracer extends HttpServerTracer<Request, Response, Request, Request> {

  private static final RestletHttpServerTracer TRACER = new RestletHttpServerTracer();

  public static RestletHttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.restlet-core-1.1";
  }

  @Override
  public @Nullable Context getServerContext(Request request) {
    Object context = request.getAttributes().get(CONTEXT_ATTRIBUTE);
    return context instanceof Context ? (Context) context : null;
  }

  @Override
  protected @Nullable Integer peerPort(Request request) {
    return request.getClientInfo().getPort();
  }

  @Override
  protected @Nullable String peerHostIp(Request request) {
    return request.getClientInfo().getAddress();
  }

  @Override
  protected String flavor(Request connection, Request request) {
    return (String) request.getAttributes().get("org.restlet.http.version");
  }

  @Override
  protected TextMapGetter<Request> getGetter() {
    return RestletExtractAdapter.GETTER;
  }

  @Override
  protected String url(Request request) {
    return request.getOriginalRef().toString();
  }

  @Override
  protected String method(Request request) {
    return request.getMethod().getName();
  }

  @Override
  protected @Nullable String requestHeader(Request request, String name) {
    return HeadersAdapter.getValue(request, name);
  }

  @Override
  protected int responseStatus(Response response) {
    return response.getStatus().getCode();
  }

  @Override
  protected void attachServerContext(Context context, Request request) {
    request.getAttributes().put(CONTEXT_ATTRIBUTE, context);
  }
}
