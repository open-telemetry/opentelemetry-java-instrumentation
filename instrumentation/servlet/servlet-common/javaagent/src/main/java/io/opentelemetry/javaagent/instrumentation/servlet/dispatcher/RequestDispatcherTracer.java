/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import io.opentelemetry.instrumentation.api.instrumenter.BaseInstrumenter;

public class RequestDispatcherTracer extends BaseInstrumenter {
  private static final RequestDispatcherTracer TRACER = new RequestDispatcherTracer();

  public static RequestDispatcherTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet";
  }
}
