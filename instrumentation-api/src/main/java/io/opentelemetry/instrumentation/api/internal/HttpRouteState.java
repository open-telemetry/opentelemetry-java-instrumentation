/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

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

  public static HttpRouteState create(
      @Nullable String method, @Nullable String route, int updatedBySourceOrder) {
    return new HttpRouteState(method, route, updatedBySourceOrder);
  }

  @Nullable private final String method;
  @Nullable private volatile String route;
  private volatile int updatedBySourceOrder;

  private HttpRouteState(
      @Nullable String method, @Nullable String route, int updatedBySourceOrder) {
    this.method = method;
    this.updatedBySourceOrder = updatedBySourceOrder;
    this.route = route;
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

  public void update(
      @SuppressWarnings("unused")
          Context context, // context is used by the javaagent bridge instrumentation
      int updatedBySourceOrder,
      String route) {
    this.updatedBySourceOrder = updatedBySourceOrder;
    this.route = route;
  }
}
