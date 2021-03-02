/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import ratpack.handling.Context;

public class RatpackTracer extends BaseTracer {
  private static final RatpackTracer TRACER = new RatpackTracer();

  public static RatpackTracer tracer() {
    return TRACER;
  }

  public void onContext(io.opentelemetry.context.Context otelContext, Context ctx) {
    String description = ctx.getPathBinding().getDescription();
    if (description == null || description.isEmpty()) {
      description = "/";
    } else if (!description.startsWith("/")) {
      description = "/" + description;
    }

    Span.fromContext(otelContext).updateName(description);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.ratpack-1.4";
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof Error && throwable.getCause() != null) {
      return throwable.getCause();
    } else {
      return throwable;
    }
  }
}
