/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.UnprocessedRequestException;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ArmeriaClientTracer
    extends HttpClientTracer<ClientRequestContext, ClientRequestContext, RequestLog> {

  ArmeriaClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  @Override
  protected String method(ClientRequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  protected @Nullable String flavor(ClientRequestContext ctx) {
    SessionProtocol protocol = ctx.sessionProtocol();
    if (protocol.isMultiplex()) {
      return "HTTP/2.0";
    } else {
      return "HTTP/1.1";
    }
  }

  @Override
  @Nullable
  protected URI url(ClientRequestContext ctx) throws URISyntaxException {
    HttpRequest request = ctx.request();
    return request != null ? request.uri() : null;
  }

  @Override
  @Nullable
  protected Integer status(RequestLog log) {
    if (log.responseCause() instanceof UnprocessedRequestException) {
      return null;
    }
    return log.responseHeaders().status().code();
  }

  @Override
  @Nullable
  protected String requestHeader(ClientRequestContext ctx, String name) {
    HttpRequest request = ctx.request();
    return request != null ? request.headers().get(name) : null;
  }

  @Override
  @Nullable
  protected String responseHeader(RequestLog log, String name) {
    return log.responseHeaders().get(name);
  }

  @Override
  protected TextMapSetter<ClientRequestContext> getSetter() {
    return ArmeriaSetter.INSTANCE;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.armeria-1.3";
  }

  private static class ArmeriaSetter implements TextMapSetter<ClientRequestContext> {

    private static final ArmeriaSetter INSTANCE = new ArmeriaSetter();

    @Override
    public void set(@Nullable ClientRequestContext ctx, String key, String value) {
      if (ctx != null) {
        ctx.addAdditionalRequestHeader(key, value);
      }
    }
  }
}
