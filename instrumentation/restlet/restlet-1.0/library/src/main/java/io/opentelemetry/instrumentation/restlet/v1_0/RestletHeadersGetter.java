/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Locale;
import org.restlet.data.Form;
import org.restlet.data.Request;

final class RestletHeadersGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    return getHeaders(carrier).getNames();
  }

  @Override
  public String get(Request carrier, String key) {

    Form headers = getHeaders(carrier);

    String value = headers.getFirstValue(key);
    if (value != null) {
      return value;
    }
    return headers.getFirstValue(key.toLowerCase(Locale.ROOT));
  }

  private static Form getHeaders(Request carrier) {
    return (Form) carrier.getAttributes().get("org.restlet.http.headers");
  }
}
