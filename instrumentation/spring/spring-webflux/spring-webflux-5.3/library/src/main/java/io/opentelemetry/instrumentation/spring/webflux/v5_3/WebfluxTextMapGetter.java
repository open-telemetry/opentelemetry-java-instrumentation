/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.internal.ExtendedTextMapGetter;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;

enum WebfluxTextMapGetter implements ExtendedTextMapGetter<ServerWebExchange> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ServerWebExchange exchange) {
    return exchange.getRequest().getHeaders().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable ServerWebExchange exchange, String key) {
    if (exchange == null) {
      return null;
    }
    return exchange.getRequest().getHeaders().getFirst(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable ServerWebExchange exchange, String key) {
    if (exchange == null) {
      return emptyIterator();
    }
    List<String> list = exchange.getRequest().getHeaders().get(key);
    return list != null ? list.iterator() : emptyIterator();
  }
}
