/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1.internal;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptySet;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Iterator;
import javax.annotation.Nullable;
import org.restlet.data.Form;
import org.restlet.data.Message;
import org.restlet.data.Parameter;
import org.restlet.data.Request;

final class RestletHeadersGetter implements TextMapGetter<Request> {

  @Override
  public Iterable<String> keys(Request carrier) {
    Form headers = getHeaders(carrier);
    return headers == null ? emptySet() : headers.getNames();
  }

  @Override
  @Nullable
  public String get(@Nullable Request carrier, String key) {
    Form headers = getHeaders(carrier);
    return headers == null ? null : headers.getFirstValue(key, true);
  }

  @Override
  public Iterator<String> getAll(@Nullable Request carrier, String key) {
    Form headers = getHeaders(carrier);
    return headers == null
        ? emptyIterator()
        : headers.subList(key, true).stream().map(Parameter::getValue).iterator();
  }

  @Nullable
  static Form getHeaders(@Nullable Message carrier) {
    if (carrier == null) {
      return null;
    }
    return (Form) carrier.getAttributes().get("org.restlet.http.headers");
  }
}
