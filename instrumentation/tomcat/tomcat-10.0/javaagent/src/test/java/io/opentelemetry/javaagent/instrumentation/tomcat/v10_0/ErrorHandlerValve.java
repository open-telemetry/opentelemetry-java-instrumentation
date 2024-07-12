/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;

class ErrorHandlerValve extends ErrorReportValve {
  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.isError()) {
      return;
    }
    try {
      response.getWriter().print(t != null ? t.getCause().getMessage() : response.getMessage());
    } catch (IOException ignored) {
      // Ignore exception when writing exception message to response fails on IO - same as is done
      // by the superclass itself and by other built-in ErrorReportValve implementations.
    }
  }
}
