/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.vertx;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class VertxTracer extends BaseTracer {
  public static final VertxTracer TRACER = new VertxTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.vertx";
  }
}
