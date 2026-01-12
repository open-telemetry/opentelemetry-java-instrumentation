/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.nats.client.impl.Headers;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import javax.annotation.Nullable;

enum NatsRequestTextMapGetter implements TextMapGetter<NatsRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(NatsRequest request) {
    Headers headers = request.getHeaders();

    if (headers == null) {
      return Collections.emptyList();
    }

    return headers.keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable NatsRequest request, String key) {
    if (request == null || request.getHeaders() == null) {
      return null;
    }

    return request.getHeaders().getFirst(key);
  }
}
