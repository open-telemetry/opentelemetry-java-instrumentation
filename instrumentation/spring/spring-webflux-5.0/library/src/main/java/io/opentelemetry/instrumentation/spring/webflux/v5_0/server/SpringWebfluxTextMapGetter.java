/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;

enum SpringWebfluxTextMapGetter implements TextMapGetter<ServerHttpRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ServerHttpRequest request) {
    return request.getHeaders().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable ServerHttpRequest request, String key) {
    if (request == null) {
      return null;
    }
    return request.getHeaders().getFirst(key);
  }
}
