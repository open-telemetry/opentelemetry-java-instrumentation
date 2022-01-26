/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import javax.annotation.Nullable;
import spark.routematch.RouteMatch;

public final class SparkRouteUpdater {

  public static void updateHttpRoute(@Nullable RouteMatch routeMatch) {
    if (routeMatch != null) {
      Context context = Context.current();
      HttpRouteHolder.updateHttpRoute(
          context, HttpRouteSource.CONTROLLER, (c, r) -> r.getMatchUri(), routeMatch);
    }
  }

  private SparkRouteUpdater() {}
}
