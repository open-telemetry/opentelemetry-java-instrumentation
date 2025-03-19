/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Collectors;

enum UndertowExchangeGetter implements ExtendedTextMapGetter<HttpServerExchange> {
  INSTANCE;

  @Override
  public Iterable<String> keys(HttpServerExchange carrier) {
    return carrier.getRequestHeaders().getHeaderNames().stream()
        .map(HttpString::toString)
        .collect(Collectors.toList());
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
