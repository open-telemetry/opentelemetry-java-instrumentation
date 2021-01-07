/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class RequestDispatcherTracer extends BaseTracer {
  private static final RequestDispatcherTracer TRACER = new RequestDispatcherTracer();

  public static RequestDispatcherTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-common";
  }
}
