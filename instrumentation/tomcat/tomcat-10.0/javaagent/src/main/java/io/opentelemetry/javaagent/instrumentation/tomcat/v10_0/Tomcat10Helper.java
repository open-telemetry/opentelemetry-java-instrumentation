/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import static io.opentelemetry.javaagent.instrumentation.tomcat.v10_0.Tomcat10Singletons.instrumenter;

import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class Tomcat10Helper extends TomcatHelper<HttpServletRequest, HttpServletResponse> {
  private static final Tomcat10Helper HELPER = new Tomcat10Helper();

  public static Tomcat10Helper helper() {
    return HELPER;
  }

  private Tomcat10Helper() {
    super(
        instrumenter(),
        Tomcat10ServletEntityProvider.INSTANCE,
        JakartaServletHttpServerTracer.tracer());
  }
}
