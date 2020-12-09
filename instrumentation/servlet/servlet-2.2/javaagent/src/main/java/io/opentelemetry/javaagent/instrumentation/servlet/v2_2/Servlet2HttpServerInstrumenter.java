/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v2_2;

import io.opentelemetry.instrumentation.servlet.ServletHttpServerInstrumenter;

public class Servlet2HttpServerInstrumenter
    extends ServletHttpServerInstrumenter<ResponseWithStatus> {
  private static final Servlet2HttpServerInstrumenter TRACER = new Servlet2HttpServerInstrumenter();

  public static Servlet2HttpServerInstrumenter tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.servlet";
  }

  @Override
  protected int responseStatus(ResponseWithStatus responseWithStatus) {
    return responseWithStatus.getStatus();
  }
}
