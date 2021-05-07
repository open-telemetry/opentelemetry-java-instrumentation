/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.RequestLog;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ArmeriaHttpAttributesExtractor
    extends HttpAttributesExtractor<RequestContext, RequestLog> {

  @Override
  protected String method(RequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  protected String url(RequestContext ctx) {
    return request(ctx).uri().toString();
  }

  @Override
  protected String target(RequestContext ctx) {
    return request(ctx).path();
  }

  @Override
  @Nullable
  protected String host(RequestContext ctx) {
    return request(ctx).authority();
  }

  @Override
  @Nullable
  protected String scheme(RequestContext ctx) {
    return request(ctx).scheme();
  }

  @Override
  @Nullable
  protected String userAgent(RequestContext ctx) {
    return request(ctx).headers().get(HttpHeaderNames.USER_AGENT);
  }

  @Override
  protected Long requestContentLength(RequestContext ctx, RequestLog requestLog) {
    return requestLog.requestLength();
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(RequestContext ctx, RequestLog requestLog) {
    return null;
  }

  @Override
  @Nullable
  protected Long statusCode(RequestContext ctx, RequestLog requestLog) {
    HttpStatus status = requestLog.responseHeaders().status();
    if (!status.equals(HttpStatus.UNKNOWN)) {
      return (long) status.code();
    }
    return null;
  }

  @Override
  protected String flavor(RequestContext ctx, RequestLog requestLog) {
    SessionProtocol protocol = ctx.sessionProtocol();
    if (protocol.isMultiplex()) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    } else {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
  }

  @Override
  protected Long responseContentLength(RequestContext ctx, RequestLog requestLog) {
    return requestLog.responseLength();
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      RequestContext ctx, RequestLog requestLog) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(RequestContext ctx, RequestLog requestLog) {
    if (ctx instanceof ServiceRequestContext) {
      return ((ServiceRequestContext) ctx).config().virtualHost().hostnamePattern();
    }
    return null;
  }

  @Override
  @Nullable
  protected String route(RequestContext ctx) {
    if (ctx instanceof ServiceRequestContext) {
      return ((ServiceRequestContext) ctx).config().route().patternString();
    }
    return null;
  }

  @Override
  @Nullable
  protected String clientIp(RequestContext ctx, RequestLog requestLog) {
    return null;
  }

  private HttpRequest request(RequestContext ctx) {
    HttpRequest request = ctx.request();
    if (request == null) {
      throw new IllegalStateException(
          "Context always has a request in decorators, this exception indicates a programming bug.");
    }
    return request;
  }
}
