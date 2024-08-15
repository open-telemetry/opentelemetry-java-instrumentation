/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import javax.servlet.Servlet;

public class TomcatServlet3SyncTest extends TomcatServlet3Test {
  @Override
  public Class<? extends Servlet> servlet() {
    return TestServlet3.Sync.class;
  }
}
