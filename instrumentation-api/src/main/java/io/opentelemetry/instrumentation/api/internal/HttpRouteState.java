/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class HttpRouteState implements ImplicitContextKeyed {

  private static final ContextKey<HttpRouteState> KEY =
      ContextKey.named("opentelemetry-http-server-route-key");

  @Nullable
  public static HttpRouteState fromContextOrNull(Context context) {
    return context.get(KEY);
  }

  public static void updateSpan(Context context, Span span) {
    HttpRouteState state = fromContextOrNull(context);
    if (state != null) {
      state.span = span;
    }
  }

  // this method is used reflectively from InstrumentationApiContextBridging
  public static HttpRouteState create(
      @Nullable String method, @Nullable String route, int updatedBySourceOrder) {
    return create(method, route, updatedBySourceOrder, null);
  }

  // this method is used reflectively from InstrumentationApiContextBridging
  public static HttpRouteState create(
      @Nullable String method, @Nullable String route, int updatedBySourceOrder, Span span) {
    return new HttpRouteState(method, route, updatedBySourceOrder, span);
  }

  @Nullable private final String method;
  @Nullable private volatile String route;
  private volatile int updatedBySourceOrder;
  @Nullable private volatile Span span;

  private HttpRouteState(
      @Nullable String method, @Nullable String route, int updatedBySourceOrder, Span span) {
    this.method = method;
    this.updatedBySourceOrder = updatedBySourceOrder;
    this.route = route;
    this.span = span;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }

  @Nullable
  public String getMethod() {
    return method;
  }

  public int getUpdatedBySourceOrder() {
    return updatedBySourceOrder;
  }

  @Nullable
  public String getRoute() {
    return route;
  }

  @Nullable
  public Span getSpan() {
    return span;
  }

  public void update(
      @SuppressWarnings("unused")
          Context context, // context is used by the javaagent bridge instrumentation
      int updatedBySourceOrder,
      String route) {
    this.updatedBySourceOrder = updatedBySourceOrder;
    this.route = route;
  }
}
