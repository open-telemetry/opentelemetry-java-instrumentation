/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spark.v2_3;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRouteSource;
import javax.annotation.Nullable;
import spark.routematch.RouteMatch;

public class SparkRouteUpdater {

  public static void updateHttpRoute(@Nullable RouteMatch routeMatch) {
    if (routeMatch != null) {
      Context context = Context.current();
      HttpServerRoute.update(context, HttpServerRouteSource.CONTROLLER, routeMatch.getMatchUri());
    }
  }

  private SparkRouteUpdater() {}
}
