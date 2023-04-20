/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v1_1;

import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import org.restlet.data.Form;
import org.restlet.data.Response;

public enum RestletResponseMutator implements HttpServerResponseMutator<Response> {
  INSTANCE;

  public static final String HEADERS_ATTRIBUTE = "org.restlet.http.headers";

  @Override
  public void appendHeader(Response response, String name, String value) {
    Form headers =
        (Form) response.getAttributes().computeIfAbsent(HEADERS_ATTRIBUTE, k -> new Form());
    String existing = headers.getValues(name);
    if (existing != null) {
      value = existing + "," + value;
    }
    headers.set(name, value, /* ignoreCase= */ true);
  }
}
