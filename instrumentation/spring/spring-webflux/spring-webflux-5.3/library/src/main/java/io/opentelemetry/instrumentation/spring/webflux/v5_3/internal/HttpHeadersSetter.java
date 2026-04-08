/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;

enum HttpHeadersSetter implements TextMapSetter<ClientRequest.Builder> {
  INSTANCE;

  @Override
  public void set(@Nullable ClientRequest.Builder carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.headers(httpHeaders -> httpHeaders.set(key, value));
  }
}
