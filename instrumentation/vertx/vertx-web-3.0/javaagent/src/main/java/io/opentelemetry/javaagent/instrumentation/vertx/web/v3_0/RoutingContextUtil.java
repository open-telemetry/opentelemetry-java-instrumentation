/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.ext.web.RoutingContext;
import javax.annotation.Nullable;

public class RoutingContextUtil {
  private static final VirtualField<RoutingContext, String> routeField =
      VirtualField.find(RoutingContext.class, String.class);

  static void setRoute(RoutingContext routingContext, @Nullable String route) {
    routeField.set(routingContext, route);
  }

  @Nullable
  public static String getRoute(RoutingContext routingContext) {
    return routeField.get(routingContext);
  }

  private RoutingContextUtil() {}
}
