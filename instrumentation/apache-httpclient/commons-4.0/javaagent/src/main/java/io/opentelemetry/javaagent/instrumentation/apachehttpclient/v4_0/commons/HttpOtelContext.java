/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import io.opentelemetry.context.Context;
import java.util.Objects;
import org.apache.http.protocol.HttpContext;

public final class HttpOtelContext {
  private static final String CONTEXT_ATTRIBUTE = "@otel.context";
  private static final String ASYNC_CLIENT_ATTRIBUTE = "@otel.async.client";

  private final HttpContext httpContext;

  private HttpOtelContext(HttpContext httpContext) {
    this.httpContext = httpContext;
  }

  public static HttpOtelContext adapt(HttpContext httpContext) {
    return new HttpOtelContext(httpContext);
  }

  public void setContext(Context context) {
    httpContext.setAttribute(CONTEXT_ATTRIBUTE, context);
  }

  public void markAsyncClient() {
    httpContext.setAttribute(ASYNC_CLIENT_ATTRIBUTE, true);
  }

  public Context getContext() {
    return getAttribute(CONTEXT_ATTRIBUTE, Context.class);
  }

  public boolean isAsyncClient() {
    return Objects.equals(getAttribute(ASYNC_CLIENT_ATTRIBUTE, Boolean.class), Boolean.TRUE);
  }

  private <T> T getAttribute(String attributeName, Class<T> clazz) {
    Object attribute = httpContext.getAttribute(attributeName);
    if (attribute == null) {
      return null;
    }
    return clazz.cast(attribute);
  }

  public void clear() {
    httpContext.removeAttribute(CONTEXT_ATTRIBUTE);
    httpContext.removeAttribute(ASYNC_CLIENT_ATTRIBUTE);
  }
}
