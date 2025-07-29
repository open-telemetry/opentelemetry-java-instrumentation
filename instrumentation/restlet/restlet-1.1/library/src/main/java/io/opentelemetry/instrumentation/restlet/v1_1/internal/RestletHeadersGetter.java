/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1.internal;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import org.restlet.data.Form;
import org.restlet.data.Message;
import org.restlet.data.Parameter;
import org.restlet.data.Request;

enum RestletHeadersGetter implements ExtendedTextMapGetter<Request> {
  INSTANCE;

  @Override
  public Iterable<String> keys(Request carrier) {
    return getHeaders(carrier).getNames();
  }

  @Override
  public String get(Request carrier, String key) {
    Form headers = getHeaders(carrier);
    return headers.getFirstValue(key, true);
  }

  @Override
  public Iterator<String> getAll(Request carrier, String key) {
    Form headers = getHeaders(carrier);
    return headers.subList(key, true).stream().map(Parameter::getValue).iterator();
  }

  static Form getHeaders(Message carrier) {
    return (Form) carrier.getAttributes().get("org.restlet.http.headers");
  }
}
