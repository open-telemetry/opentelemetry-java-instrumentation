/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.restlet.v2_0;

import io.opentelemetry.instrumentation.restlet.v2_0.internal.MessageAttributesAccessor;
import io.opentelemetry.javaagent.bootstrap.http.HttpServerResponseMutator;
import java.util.Map;
import org.restlet.Response;
import org.restlet.data.Form;

public enum RestletResponseMutator implements HttpServerResponseMutator<Response> {
  INSTANCE;

  public static final String HEADERS_ATTRIBUTE = "org.restlet.http.headers";

  @Override
  public void appendHeader(Response response, String name, String value) {
    Map<String, Object> attributes = MessageAttributesAccessor.getAttributes(response);
    if (attributes == null) {
      // should never happen in practice
      return;
    }
    Form headers = (Form) attributes.computeIfAbsent(HEADERS_ATTRIBUTE, k -> new Form());
    String existing = headers.getValues(name);
    if (existing != null) {
      value = existing + "," + value;
    }
    headers.set(name, value, /* ignoreCase= */ true);
  }
}
