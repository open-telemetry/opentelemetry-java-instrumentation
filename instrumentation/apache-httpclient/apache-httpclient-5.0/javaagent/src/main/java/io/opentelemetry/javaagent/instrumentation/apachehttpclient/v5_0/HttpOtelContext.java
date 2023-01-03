/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import java.util.Objects;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.protocol.HttpCoreContext;

public final class HttpOtelContext {
  private static final String CONTEXT_ATTRIBUTE = "@otel.context";
  private static final String ASYNC_CLIENT_ATTRIBUTE = "@otel.async.client";

  private final HttpCoreContext httpContext;

  private HttpOtelContext(HttpContext httpContext) {
    this.httpContext = HttpCoreContext.adapt(httpContext);
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
    return httpContext.getAttribute(CONTEXT_ATTRIBUTE, Context.class);
  }

  public boolean isAsyncClient() {
    Boolean attribute = httpContext.getAttribute(ASYNC_CLIENT_ATTRIBUTE, Boolean.class);
    return Objects.equals(attribute, Boolean.TRUE);
  }

  public void clear() {
    httpContext.removeAttribute(CONTEXT_ATTRIBUTE);
    httpContext.removeAttribute(ASYNC_CLIENT_ATTRIBUTE);
  }
}
