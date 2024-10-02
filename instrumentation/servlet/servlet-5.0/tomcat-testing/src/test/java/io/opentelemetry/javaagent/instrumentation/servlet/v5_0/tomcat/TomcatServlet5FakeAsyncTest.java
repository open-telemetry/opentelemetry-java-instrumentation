/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;
import org.junit.jupiter.api.extension.RegisterExtension;

class TomcatServlet5FakeAsyncTest extends TomcatServlet5Test {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.FakeAsync.class;
  }
}
