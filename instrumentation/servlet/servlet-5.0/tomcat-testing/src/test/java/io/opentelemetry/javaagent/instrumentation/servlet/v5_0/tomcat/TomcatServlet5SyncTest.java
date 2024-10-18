/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.tomcat;

import io.opentelemetry.javaagent.instrumentation.servlet.v5_0.TestServlet5;
import jakarta.servlet.Servlet;

class TomcatServlet5SyncTest extends TomcatServlet5Test {

  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet5.Sync.class;
  }
}
