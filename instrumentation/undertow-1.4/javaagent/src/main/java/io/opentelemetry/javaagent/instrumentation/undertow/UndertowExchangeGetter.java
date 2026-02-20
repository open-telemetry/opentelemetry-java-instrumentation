/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import java.util.Collections;
import java.util.Iterator;

enum UndertowExchangeGetter implements TextMapGetter<HttpServerExchange> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServerExchange carrier) {
    return carrier.getRequestHeaders().getHeaderNames().stream()
        .map(HttpString::toString)
        .collect(toList());
  }

  @Override
  public String get(HttpServerExchange carrier, String key) {
    return carrier.getRequestHeaders().getFirst(key);
  }

  @Override
  public Iterator<String> getAll(HttpServerExchange carrier, String key) {
    HeaderValues headerValues = carrier.getRequestHeaders().get(key);
    return headerValues != null ? headerValues.iterator() : Collections.emptyIterator();
  }
}
