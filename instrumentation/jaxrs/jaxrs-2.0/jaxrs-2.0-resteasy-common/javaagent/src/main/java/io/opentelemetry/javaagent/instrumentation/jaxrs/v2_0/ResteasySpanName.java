/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.NESTED_CONTROLLER;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import javax.annotation.Nullable;

public final class ResteasySpanName implements ServerSpanNameSupplier<String> {

  public static final ResteasySpanName INSTANCE = new ResteasySpanName();

  public void updateServerSpanName(Context context, String name) {
    if (name != null) {
      ServerSpanNaming.updateServerSpanName(context, NESTED_CONTROLLER, this, name);
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
