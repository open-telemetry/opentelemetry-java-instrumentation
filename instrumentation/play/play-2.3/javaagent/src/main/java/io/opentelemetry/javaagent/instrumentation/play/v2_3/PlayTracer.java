/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.play.v2_3;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import play.api.mvc.Request;
import scala.Option;

public class PlayTracer extends BaseTracer {
  private static final PlayTracer TRACER = new PlayTracer();

  public static PlayTracer tracer() {
    return TRACER;
  }

  public Span updateSpanName(Span span, Request<?> request) {
    Option<String> pathOption = request.tags().get("ROUTE_PATTERN");
    if (!pathOption.isEmpty()) {
      String path = pathOption.get();
      span.updateName(path);
    }
    return span;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.play-2.3";
  }
}
