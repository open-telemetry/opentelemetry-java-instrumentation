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
            return null;
          }

          @Override
          public boolean isRequestAsyncStarted(HttpServletRequest request) {
            return false;
          }

          @Override
          public void addRequestAsyncListener(HttpServletRequest request,
              ServletAsyncListener<Void> listener) {

          }

          @Override
          public int getResponseStatus(Void unused) {
            return 0;
          }

          @Override
          public boolean isResponseCommitted(Void unused) {
            return false;
          }
        });
  }

  @Override
  protected String getInstrumentationName() {
    return null;
  }
}
