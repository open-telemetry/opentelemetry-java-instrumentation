/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteGetter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerRouteSource;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import io.opentelemetry.javaagent.bootstrap.servlet.ServletContextPath;
import javax.annotation.Nullable;

public final class ResteasySpanName implements HttpServerRouteGetter<String> {

  public static final ResteasySpanName INSTANCE = new ResteasySpanName();

  public void updateServerSpanName(Context context, String name) {
    if (name != null) {
      HttpServerRoute.update(context, HttpServerRouteSource.NESTED_CONTROLLER, this, name);
    }
  }

  @Override
  @Nullable
  public String get(Context context, String name) {
    if (name.isEmpty()) {
      return null;
    }
    return ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, name));
  }

  private ResteasySpanName() {}
}
