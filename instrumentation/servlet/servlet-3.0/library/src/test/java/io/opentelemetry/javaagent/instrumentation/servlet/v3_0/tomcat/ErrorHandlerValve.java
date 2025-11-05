/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.tomcat;

import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// public, because it's loaded by reflection
public class ErrorHandlerValve extends ErrorReportValve {

  private static final Logger logger = LoggerFactory.getLogger(ErrorHandlerValve.class);

  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.isError()) {
      return;
    }

    try {
      response.getWriter().print(t != null ? t.getCause().getMessage() : response.getMessage());
    } catch (IOException e) {
      logger.error("Failed to write error response", e);
    }
  }
}
