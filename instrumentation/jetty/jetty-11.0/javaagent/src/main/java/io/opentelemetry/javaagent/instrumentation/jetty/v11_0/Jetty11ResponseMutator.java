/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import jakarta.servlet.http.HttpServletResponse;

public enum Jetty11ResponseMutator implements HttpServerResponseMutator<HttpServletResponse> {
  INSTANCE;

  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }
}
