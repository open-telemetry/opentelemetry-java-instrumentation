/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;
import java.util.function.Supplier;

public class JaxrsServerSpanNaming {

  public static Supplier<String> getServerSpanNameSupplier(
      Context context, HandlerData handlerData) {
    return () -> {
      String pathBasedSpanName = handlerData.getServerSpanName();
      // If path based name is empty skip prepending context path so that path based name would
      // remain as an empty string for which we skip updating span name. Path base span name is
      // empty when method and class don't have a jax-rs path annotation, this can happen when
      // creating an "abort" span, see RequestContextHelper.
      if (!pathBasedSpanName.isEmpty()) {
        pathBasedSpanName = JaxrsContextPath.prepend(context, pathBasedSpanName);
        pathBasedSpanName = ServletContextPath.prepend(context, pathBasedSpanName);
      }
      return pathBasedSpanName;
    };
  }

  private JaxrsServerSpanNaming() {}
}
