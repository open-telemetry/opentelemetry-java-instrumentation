/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver.internal;

import com.sun.net.httpserver.HttpExchange;
import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

enum ExchangeContextGetter implements ExtendedTextMapGetter<HttpExchange> {
  INSTANCE;

  @Override
  public Iterable<String> keys(@Nullable HttpExchange exchange) {
    if (exchange == null) {
      return Collections.emptyList();
    }
    return exchange.getRequestHeaders().keySet().stream().collect(Collectors.toList());
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
      return Collections.emptyIterator();
    }
    List<String> list = carrier.getRequestHeaders().get(key);

    return list != null ? list.iterator() : Collections.emptyIterator();
  }
}
