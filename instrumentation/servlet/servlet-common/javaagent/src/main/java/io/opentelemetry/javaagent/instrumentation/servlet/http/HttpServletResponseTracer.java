/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.http;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HttpServletResponseTracer extends BaseTracer {
  private static final HttpServletResponseTracer TRACER = new HttpServletResponseTracer();

  public static HttpServletResponseTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-common";
  }
}
