/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import javax.servlet.http.HttpServletResponse;

public class Servlet3HttpServerResponseMutator
    implements HttpServerResponseMutator<HttpServletResponse> {

  public static final Servlet3HttpServerResponseMutator INSTANCE =
      new Servlet3HttpServerResponseMutator();

  private Servlet3HttpServerResponseMutator() {}
  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }
}
