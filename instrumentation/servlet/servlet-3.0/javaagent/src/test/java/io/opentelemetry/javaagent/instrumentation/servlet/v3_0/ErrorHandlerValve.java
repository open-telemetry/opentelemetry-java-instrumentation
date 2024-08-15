/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ErrorReportValve;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

public class ErrorHandlerValve extends ErrorReportValve {
  @Override
  protected void report(Request request, Response response, Throwable t) {
    if (response.getStatus() < 400 || response.getContentWritten() > 0 || !response.isError()) {
      return;
    }

    try {
      response
          .getWriter()
          .print(
              DefaultGroovyMethods.asBoolean(t)
                  ? t.getCause().getMessage()
                  : response.getMessage());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
