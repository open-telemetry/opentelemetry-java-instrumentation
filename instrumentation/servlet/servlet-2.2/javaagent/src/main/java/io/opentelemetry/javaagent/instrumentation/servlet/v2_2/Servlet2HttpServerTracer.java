/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;

public class Servlet2HttpServerTracer extends ServletHttpServerTracer<ResponseWithStatus> {
  private static final Servlet2HttpServerTracer TRACER = new Servlet2HttpServerTracer();

  public static Servlet2HttpServerTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet-2.2";
  }

  @Override
  protected int responseStatus(ResponseWithStatus responseWithStatus) {
    return responseWithStatus.getStatus();
  }

  @Override
  protected boolean isResponseCommitted(ResponseWithStatus responseWithStatus) {
    return responseWithStatus.getResponse().isCommitted();
  }
}
