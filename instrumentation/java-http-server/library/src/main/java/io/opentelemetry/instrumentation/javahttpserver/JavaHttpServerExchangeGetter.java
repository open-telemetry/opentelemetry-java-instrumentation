/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptyList;

import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;

enum JavaHttpServerExchangeGetter implements ExtendedTextMapGetter<HttpExchange> {
  INSTANCE;

  @Override
  public Iterable<String> keys(@Nullable HttpExchange exchange) {
    if (exchange == null) {
      return emptyList();
    }
    return exchange.getRequestHeaders().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable HttpExchange carrier, String key) {
    if (carrier == null) {
      return null;
    }

    List<String> list = carrier.getRequestHeaders().get(key);
    return list != null ? list.get(0) : null;
  }

  @Override
  public Iterator<String> getAll(@Nullable HttpExchange carrier, String key) {
    if (carrier == null) {
      return emptyIterator();
    }

    List<String> list = carrier.getRequestHeaders().get(key);
    return list != null ? list.iterator() : emptyIterator();
  }
}
