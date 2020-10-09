/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.dispatcher;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class RequestDispatcherTracer extends BaseTracer {
  public static final RequestDispatcherTracer TRACER = new RequestDispatcherTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet";
  }
}
