/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class VertxTracer extends BaseInstrumenter {
  private static final VertxTracer TRACER = new VertxTracer();

  public static VertxTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.vertx-web";
  }
}
