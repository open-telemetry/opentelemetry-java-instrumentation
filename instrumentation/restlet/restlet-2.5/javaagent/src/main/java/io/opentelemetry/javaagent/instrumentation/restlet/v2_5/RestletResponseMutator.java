/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_5;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.restlet.Response;
import org.restlet.data.Header;
import org.restlet.util.Series;

public enum RestletResponseMutator implements HttpServerResponseMutator<Response> {
  INSTANCE;

  @Override
  public void appendHeader(Response response, String name, String value) {
    Series<Header> headers = response.getHeaders();
    String existing = headers.getValues(name);
    if (existing != null) {
      value = existing + "," + value;
    }
    headers.set(name, value);
  }
}
