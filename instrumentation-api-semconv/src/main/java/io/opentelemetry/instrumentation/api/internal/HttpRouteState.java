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

  public static HttpRouteState create(int updatedBySourceOrder, @Nullable String route) {
    return new HttpRouteState(updatedBySourceOrder, route);
  }

  private volatile int updatedBySourceOrder;
  @Nullable private volatile String route;

  private HttpRouteState(int updatedBySourceOrder, @Nullable String route) {
    this.updatedBySourceOrder = updatedBySourceOrder;
    this.route = route;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
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
