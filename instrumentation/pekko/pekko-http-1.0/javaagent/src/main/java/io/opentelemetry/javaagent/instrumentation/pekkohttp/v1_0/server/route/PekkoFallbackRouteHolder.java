/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.route;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;

public class PekkoFallbackRouteHolder implements ImplicitContextKeyed {
  private static final ContextKey<PekkoFallbackRouteHolder> KEY =
      named("opentelemetry-pekko-route-fallback");

  private PekkoRouteHolder fallback;

  public static PekkoFallbackRouteHolder get(Context context) {
    return context.get(KEY);
  }

  public String route() {
    return fallback != null ? fallback.route() : null;
  }

  public void setFallback(PekkoRouteHolder fallback) {
    this.fallback = fallback;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
