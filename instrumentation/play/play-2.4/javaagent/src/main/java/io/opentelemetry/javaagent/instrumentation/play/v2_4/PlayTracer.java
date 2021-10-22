/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_4;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import play.api.mvc.Request;
import scala.Option;

public class PlayTracer extends BaseTracer {
  private static final PlayTracer TRACER = new PlayTracer();

  public static PlayTracer tracer() {
    return TRACER;
  }

  public void updateSpanName(Span span, Request<?> request) {
    if (request != null) {
      Option<String> pathOption = request.tags().get("ROUTE_PATTERN");
      if (!pathOption.isEmpty()) {
        String path = pathOption.get();
        span.updateName(path);
      } else {
        span.updateName(request.path());
      }
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.play-2.4";
  }
}
