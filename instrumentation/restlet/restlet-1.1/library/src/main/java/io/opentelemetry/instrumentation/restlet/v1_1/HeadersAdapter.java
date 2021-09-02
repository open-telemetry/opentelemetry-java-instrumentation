/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import java.util.Locale;
import org.restlet.data.Form;
import org.restlet.data.Request;

final class HeadersAdapter {

  static Form getHeaders(Request request) {
    return (Form) request.getAttributes().get("org.restlet.http.headers");
  }

  static String getValue(Request request, String name) {
    Form headers = getHeaders(request);
    String value = headers.getFirstValue(name);
    if (value != null) {
      return value;
    }
    return headers.getFirstValue(name.toLowerCase(Locale.ROOT));
  }
}
