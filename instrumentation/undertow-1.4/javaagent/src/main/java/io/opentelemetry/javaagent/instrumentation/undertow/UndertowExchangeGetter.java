/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static java.util.Collections.emptyIterator;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import java.util.Iterator;
import javax.annotation.Nullable;

class UndertowExchangeGetter implements TextMapGetter<HttpServerExchange> {
  @Override
  public Iterable<String> keys(HttpServerExchange carrier) {
    return carrier.getRequestHeaders().getHeaderNames().stream()
        .map(HttpString::toString)
        .collect(toList());
  }

  @Override
  @Nullable
  public String get(@Nullable HttpServerExchange carrier, String key) {
    if (carrier == null) {
      return null;
    }
    return carrier.getRequestHeaders().getFirst(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpServerExchange carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }
    HeaderValues headerValues = carrier.getRequestHeaders().get(key);
    return headerValues != null ? headerValues.iterator() : emptyIterator();
  }
}
