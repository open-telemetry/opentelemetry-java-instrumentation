/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0;

import io.opentelemetry.instrumentation.servlet.v3_0.copied.CallDepth;
import io.opentelemetry.instrumentation.servlet.v3_0.copied.Servlet3ResponseAdviceScope;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

/// Wrapper around [HttpServletResponse].
public class OtelHttpServletResponse extends HttpServletResponseWrapper {

  public OtelHttpServletResponse(HttpServletResponse response) {
    super(response);
  }

  @Override
  public void sendError(int sc, String msg) throws IOException {
    Servlet3ResponseAdviceScope scope =
        new Servlet3ResponseAdviceScope(
            CallDepth.forClass(HttpServletResponse.class), this.getClass(), "sendError");
    Throwable throwable = null;
    try {
      super.sendError(sc, msg);
    } catch (Throwable ex) {
      throwable = ex;
      throw ex;
    } finally {
      scope.exit(throwable);
    }
  }

  @Override
  public void sendError(int sc) throws IOException {
    Servlet3ResponseAdviceScope scope =
        new Servlet3ResponseAdviceScope(
            CallDepth.forClass(HttpServletResponse.class), this.getClass(), "sendError");
    Throwable throwable = null;
    try {
      super.sendError(sc);
    } catch (Throwable ex) {
      throwable = ex;
      throw ex;
    } finally {
      scope.exit(throwable);
    }
  }

  @Override
  public void sendRedirect(String location) throws IOException {
    Servlet3ResponseAdviceScope scope =
        new Servlet3ResponseAdviceScope(
            CallDepth.forClass(HttpServletResponse.class), this.getClass(), "sendRedirect");
    Throwable throwable = null;
    try {
      super.sendRedirect(location);
    } catch (Throwable ex) {
      throwable = ex;
      throw ex;
    } finally {
      scope.exit(throwable);
    }
  }
}
