/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

enum ArmeriaHttpServerAttributesGetter
    implements HttpServerAttributesGetter<RequestContext, RequestLog> {
  INSTANCE;

  @Override
  public String getMethod(RequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  public String getTarget(RequestContext ctx) {
    return request(ctx).path();
  }

  @Override
  @Nullable
  public String getScheme(RequestContext ctx) {
    return request(ctx).scheme();
  }

  @Override
  public List<String> getRequestHeader(RequestContext ctx, String name) {
    return request(ctx).headers().getAll(name);
  }

  @Override
  @Nullable
  public Integer getStatusCode(
      RequestContext ctx, RequestLog requestLog, @Nullable Throwable error) {
    HttpStatus status = requestLog.responseHeaders().status();
    if (!status.equals(HttpStatus.UNKNOWN)) {
      return status.code();
    }
    return null;
  }

  @Override
  public String getFlavor(RequestContext ctx) {
    SessionProtocol protocol = ctx.sessionProtocol();
    if (protocol.isMultiplex()) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    } else {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
  }

  @Override
  public List<String> getResponseHeader(RequestContext ctx, RequestLog requestLog, String name) {
    return requestLog.responseHeaders().getAll(name);
  }

  @Override
  @Nullable
  public String getRoute(RequestContext ctx) {
    if (ctx instanceof ServiceRequestContext) {
      return ((ServiceRequestContext) ctx).config().route().patternString();
    }
    return null;
  }

  private static HttpRequest request(RequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}
