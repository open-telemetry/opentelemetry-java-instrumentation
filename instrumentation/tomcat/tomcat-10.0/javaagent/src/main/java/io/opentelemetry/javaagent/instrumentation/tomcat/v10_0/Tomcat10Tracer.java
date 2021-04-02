/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import io.opentelemetry.instrumentation.tomcat.common.TomcatTracer;

public class Tomcat10Tracer extends TomcatTracer {
  private static final Tomcat10Tracer TRACER = new Tomcat10Tracer();

  public static Tomcat10Tracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.tomcat-10.0";
  }
}
