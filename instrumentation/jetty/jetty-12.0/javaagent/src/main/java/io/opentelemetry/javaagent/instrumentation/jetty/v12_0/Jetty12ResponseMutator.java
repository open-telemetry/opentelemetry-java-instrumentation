/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.eclipse.jetty.server.Response;

public enum Jetty12ResponseMutator implements HttpServerResponseMutator<Response> {
  INSTANCE;

  @Override
  public void appendHeader(Response response, String name, String value) {
    response.getHeaders().add(name, value);
  }
}
