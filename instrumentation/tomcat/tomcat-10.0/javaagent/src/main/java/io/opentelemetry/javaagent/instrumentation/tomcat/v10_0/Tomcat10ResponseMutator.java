/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v10_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.apache.coyote.Response;

public class Tomcat10ResponseMutator implements HttpServerResponseMutator<Response> {
  public static final Tomcat10ResponseMutator INSTANCE = new Tomcat10ResponseMutator();

  private Tomcat10ResponseMutator() {}

  @Override
  public void appendHeader(Response response, String name, String value) {
    response.addHeader(name, value);
  }
}
