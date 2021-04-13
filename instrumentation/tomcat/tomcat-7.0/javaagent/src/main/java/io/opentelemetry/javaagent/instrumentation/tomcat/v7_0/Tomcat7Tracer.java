/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatTracer;

public class Tomcat7Tracer extends TomcatTracer {
  private static final Tomcat7Tracer TRACER = new Tomcat7Tracer();

  public static TomcatTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.tomcat-7.0";
  }
}
