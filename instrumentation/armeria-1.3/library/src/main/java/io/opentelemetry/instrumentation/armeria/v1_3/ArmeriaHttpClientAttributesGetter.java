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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

final class ArmeriaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<RequestContext, RequestLog> {

  @Override
  public String method(RequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  public String url(RequestContext ctx) {
    return request(ctx).uri().toString();
  }

  @Override
  public List<String> requestHeader(RequestContext ctx, String name) {
    return request(ctx).headers().getAll(name);
  }

  @Override
  @Nullable
  public Long requestContentLength(RequestContext ctx, @Nullable RequestLog requestLog) {
    if (requestLog == null) {
      return null;
    }
    return requestLog.requestLength();
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      RequestContext ctx, @Nullable RequestLog requestLog) {
    return null;
  }

  @Override
  @Nullable
  public Integer statusCode(RequestContext ctx, RequestLog requestLog) {
    HttpStatus status = requestLog.responseHeaders().status();
    if (!status.equals(HttpStatus.UNKNOWN)) {
      return status.code();
    }
    return null;
  }

  @Override
  public String flavor(RequestContext ctx, @Nullable RequestLog requestLog) {
    SessionProtocol protocol = ctx.sessionProtocol();
    if (protocol.isMultiplex()) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    } else {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
  }

  @Override
  public Long responseContentLength(RequestContext ctx, RequestLog requestLog) {
    return requestLog.responseLength();
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(RequestContext ctx, RequestLog requestLog) {
    return null;
  }

  @Override
  public List<String> responseHeader(RequestContext ctx, RequestLog requestLog, String name) {
    return requestLog.responseHeaders().getAll(name);
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
