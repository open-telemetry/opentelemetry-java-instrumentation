/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import javax.servlet.http.HttpServletResponse;

public enum Servlet3HttpServerResponseMutator
    implements HttpServerResponseMutator<HttpServletResponse> {
  INSTANCE;

  @Override
  public void appendHeader(HttpServletResponse response, String name, String value) {
    response.addHeader(name, value);
  }
}
