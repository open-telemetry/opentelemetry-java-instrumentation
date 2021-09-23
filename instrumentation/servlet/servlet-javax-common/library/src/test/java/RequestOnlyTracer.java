/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.servlet.ServletAsyncListener;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletAccessor;
import io.opentelemetry.instrumentation.servlet.javax.JavaxServletHttpServerTracer;
import javax.servlet.http.HttpServletRequest;

public class RequestOnlyTracer extends JavaxServletHttpServerTracer<Void> {
  public RequestOnlyTracer() {
    super(
        new JavaxServletAccessor<Void>() {
          @Override
          public Integer getRequestRemotePort(HttpServletRequest httpServletRequest) {
            throw new UnsupportedOperationException();
          }

          @Override
          public void addRequestAsyncListener(
              HttpServletRequest request, ServletAsyncListener<Void> listener, Object response) {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getResponseStatus(Void unused) {
            throw new UnsupportedOperationException();
          }

          @Override
          public String getResponseHeader(Void unused, String name) {
            throw new UnsupportedOperationException();
          }

          @Override
          public boolean isResponseCommitted(Void unused) {
            throw new UnsupportedOperationException();
          }
        });
  }

  @Override
  protected String getInstrumentationName() {
    return "test";
  }
}
