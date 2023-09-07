/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.sparkjava;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource;
import javax.annotation.Nullable;
import spark.routematch.RouteMatch;

public final class SparkRouteUpdater {

  public static void updateHttpRoute(@Nullable RouteMatch routeMatch) {
    if (routeMatch != null) {
      Context context = Context.current();
      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, routeMatch.getMatchUri());
    }
  }

  private SparkRouteUpdater() {}
}
