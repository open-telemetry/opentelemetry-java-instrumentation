/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.eclipse.jetty.server.Response;

class Jetty12ResponseMutator implements HttpServerResponseMutator<Response> {

  public static final Jetty12ResponseMutator INSTANCE = new Jetty12ResponseMutator();

  private Jetty12ResponseMutator() {}

  @Override
  public void appendHeader(Response response, String name, String value) {
    response.getHeaders().add(name, value);
  }
}
