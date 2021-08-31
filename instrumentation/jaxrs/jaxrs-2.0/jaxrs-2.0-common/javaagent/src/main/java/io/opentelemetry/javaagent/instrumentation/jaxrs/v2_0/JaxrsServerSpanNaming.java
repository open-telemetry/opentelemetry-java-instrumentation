/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.instrumentation.api.servlet.ServerSpanNameSupplier;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;

public class JaxrsServerSpanNaming {

  public static final ServerSpanNameSupplier<HandlerData> SERVER_SPAN_NAME =
      (context, handlerData) -> {
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

  private JaxrsServerSpanNaming() {}
}
