/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import static java.util.Collections.emptyIterator;

import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class ActivejHttpServerRequestGetter implements TextMapGetter<HttpRequest> {

  @Override
  public Iterable<String> keys(HttpRequest httpRequest) {
    return httpRequest.getHeaders().stream().map(h -> h.getKey().toString()).toList();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }

    return carrier.getHeader(HttpHeaders.of(key));
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpRequest carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }

    HttpHeader httpHeader = HttpHeaders.of(key);
    List<String> values = new ArrayList<>();
    for (Map.Entry<HttpHeader, HttpHeaderValue> entry : carrier.getHeaders()) {
      if (httpHeader.equals(entry.getKey())) {
        values.add(entry.getValue().toString());
      }
    }
    return values.iterator();
  }
}
