/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import io.opentelemetry.context.propagation.TextMapGetter;
import org.restlet.data.Request;

final class RestletExtractAdapter implements TextMapGetter<Request> {

  static final RestletExtractAdapter GETTER = new RestletExtractAdapter();

  @Override
  public Iterable<String> keys(Request carrier) {
    return HeadersAdapter.getHeaders(carrier).getNames();
  }

  @Override
  public String get(Request carrier, String key) {
    return HeadersAdapter.getValue(carrier, key);
  }
}
