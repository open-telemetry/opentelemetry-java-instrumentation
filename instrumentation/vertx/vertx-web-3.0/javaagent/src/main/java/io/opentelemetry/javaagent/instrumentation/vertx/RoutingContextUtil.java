/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.ext.web.RoutingContext;

public final class RoutingContextUtil {
  private static final VirtualField<RoutingContext, String> routeField =
      VirtualField.find(RoutingContext.class, String.class);

  public static void setRoute(RoutingContext routingContext, String route) {
    routeField.set(routingContext, route);
  }

  public static String getRoute(RoutingContext routingContext) {
    return routeField.get(routingContext);
  }

  private RoutingContextUtil() {}
}
