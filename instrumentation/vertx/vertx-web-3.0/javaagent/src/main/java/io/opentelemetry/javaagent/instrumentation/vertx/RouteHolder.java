/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;

public final class RouteHolder implements ImplicitContextKeyed {
  private static final ContextKey<RouteHolder> KEY = named("opentelemetry-vertx-route");

  private String route;

  private RouteHolder(String route) {
    this.route = route;
  }

  public static Context init(Context context, String route) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new RouteHolder(route));
  }

  public static String get(Context context) {
    RouteHolder holder = context.get(KEY);
    return holder != null ? holder.route : null;
  }

  public static void set(Context context, String route) {
    RouteHolder holder = context.get(KEY);
    if (holder != null) {
      holder.route = route;
    }
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
