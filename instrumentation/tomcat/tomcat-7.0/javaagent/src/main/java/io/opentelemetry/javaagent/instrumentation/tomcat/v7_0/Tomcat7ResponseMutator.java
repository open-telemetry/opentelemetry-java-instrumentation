/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.apache.coyote.Response;

public class Tomcat7ResponseMutator implements HttpServerResponseMutator<Response> {
  public static final Tomcat7ResponseMutator INSTANCE = new Tomcat7ResponseMutator();

  private Tomcat7ResponseMutator() {}

  @Override
  public void appendHeader(Response response, String name, String value) {
    response.addHeader(name, value);
  }
}
