/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3;

import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.logging.RequestLog;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

enum ArmeriaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<RequestContext, RequestLog> {
  INSTANCE;

  @Override
  public String getMethod(RequestContext ctx) {
    return ctx.method().name();
  }

  @Override
  public String getUrl(RequestContext ctx) {
    HttpRequest request = request(ctx);
    StringBuilder uri = new StringBuilder();
    String scheme = request.scheme();
    if (scheme != null) {
      uri.append(scheme).append("://");
    }
    String authority = request.authority();
    if (authority != null) {
      uri.append(authority);
    }
    uri.append(request.path());
    return uri.toString();
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
  public List<String> getResponseHeader(RequestContext ctx, RequestLog requestLog, String name) {
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
