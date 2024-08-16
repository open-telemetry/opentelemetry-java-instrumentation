/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.jetty;

import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat.TestServlet3;
import javax.servlet.Servlet;

public class JettyServlet3AsyncTest extends JettyServlet3Test {

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setVerifyServerSpanEndTime(false);
  }

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet3.Async.class;
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return false;
  }

  @Override
  public boolean isAsyncTest() {
    return true;
  }
}
