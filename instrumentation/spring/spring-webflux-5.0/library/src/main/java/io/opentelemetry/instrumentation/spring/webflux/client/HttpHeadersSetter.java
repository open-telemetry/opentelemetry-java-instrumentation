/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.web.reactive.function.client.ClientRequest;

enum HttpHeadersSetter implements TextMapSetter<ClientRequest.Builder> {
  INSTANCE;

  @Override
  public void set(ClientRequest.Builder carrier, String key, String value) {
    carrier.headers(httpHeaders -> httpHeaders.set(key, value));
  }
}
