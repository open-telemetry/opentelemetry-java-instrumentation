/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.springframework.web.reactive.function.client.ClientRequest;

class HttpHeadersInjectAdapter implements TextMapSetter<ClientRequest.Builder> {

  public static final HttpHeadersInjectAdapter SETTER = new HttpHeadersInjectAdapter();

  @Override
  public void set(ClientRequest.Builder carrier, String key, String value) {
    carrier.header(key, value);
  }
}
