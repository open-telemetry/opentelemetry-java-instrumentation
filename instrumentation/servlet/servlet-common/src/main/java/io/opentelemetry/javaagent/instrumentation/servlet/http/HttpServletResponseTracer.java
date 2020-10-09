/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.http;

import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HttpServletResponseTracer extends BaseTracer {
  public static final HttpServletResponseTracer TRACER = new HttpServletResponseTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet";
  }
}
