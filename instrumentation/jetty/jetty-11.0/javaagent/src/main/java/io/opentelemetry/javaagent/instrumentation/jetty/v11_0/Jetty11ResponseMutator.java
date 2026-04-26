/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v11_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import jakarta.servlet.http.HttpServletResponse;

final class Jetty11ResponseMutator implements HttpServerResponseMutator<HttpServletResponse> {

  static final Jetty11ResponseMutator INSTANCE = new Jetty11ResponseMutator();

  private Jetty11ResponseMutator() {}

  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }
}
