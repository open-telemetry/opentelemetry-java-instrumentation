/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Accessor;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.Servlet5Singletons;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatHelper;
import io.opentelemetry.javaagent.instrumentation.tomcat.common.TomcatInstrumenterFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class Tomcat10Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-10.0";
  private static final Instrumenter<Request, Response> INSTRUMENTER =
      TomcatInstrumenterFactory.create(
          INSTRUMENTATION_NAME, Servlet5Accessor.INSTANCE, Tomcat10ServletEntityProvider.INSTANCE);
  private static final TomcatHelper<HttpServletRequest, HttpServletResponse> HELPER =
      new TomcatHelper<>(
          INSTRUMENTER, Tomcat10ServletEntityProvider.INSTANCE, Servlet5Singletons.helper());

  public static TomcatHelper<HttpServletRequest, HttpServletResponse> helper() {
    return HELPER;
  }

  private Tomcat10Singletons() {}
}
