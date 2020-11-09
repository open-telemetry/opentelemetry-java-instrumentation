/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.http;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HttpServletTracer extends BaseTracer {
  private static final HttpServletTracer TRACER = new HttpServletTracer();

  public static HttpServletTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet";
  }
}
