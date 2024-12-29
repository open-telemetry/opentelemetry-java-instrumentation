/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.jetty12;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;

class Jetty12Servlet5AsyncTest extends Jetty12Servlet5Test {

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Async.class;
  }

  @Override
  public boolean errorEndpointUsesSendError() {
    return false;
  }
}
