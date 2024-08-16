/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import javax.servlet.Servlet;
import org.junit.jupiter.api.extension.RegisterExtension;

public class TomcatServlet3FakeAsyncTest extends TomcatServlet3Test {

  @RegisterExtension
  protected static final InstrumentationExtension testing =
      HttpServerInstrumentationExtension.forAgent();

  public TomcatServlet3FakeAsyncTest() {
    super(testing);
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet3.FakeAsync.class;
  }
}
