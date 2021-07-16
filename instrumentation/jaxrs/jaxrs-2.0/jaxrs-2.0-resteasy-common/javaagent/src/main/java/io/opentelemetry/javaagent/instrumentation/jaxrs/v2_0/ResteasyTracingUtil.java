/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import io.opentelemetry.instrumentation.api.servlet.ServletContextPath;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.bootstrap.jaxrs.JaxrsContextPath;

public final class ResteasyTracingUtil {

  private ResteasyTracingUtil() {}

  public static void updateServerSpanName(Context context, String name) {
    if (name == null || name.isEmpty()) {
      return;
    }

    Span serverSpan = ServerSpan.fromContextOrNull(context);
    if (serverSpan == null) {
      return;
    }

    serverSpan.updateName(
        ServletContextPath.prepend(context, JaxrsContextPath.prepend(context, name)));
    // mark span name as updated from controller to avoid JaxRsAnnotationsTracer updating it
    ServerSpanNaming.updateSource(context, ServerSpanNaming.Source.CONTROLLER);
  }
}
