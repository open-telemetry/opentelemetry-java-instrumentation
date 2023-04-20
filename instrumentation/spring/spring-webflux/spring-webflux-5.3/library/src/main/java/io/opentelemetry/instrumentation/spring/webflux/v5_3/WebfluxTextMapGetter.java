/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;

enum WebfluxTextMapGetter implements TextMapGetter<ServerWebExchange> {
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
}
