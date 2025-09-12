/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum NatsRequestTextMapSetter implements TextMapSetter<NatsRequest> {
  INSTANCE;

  @Override
  /* Can not work if getHeaders doesn't return a writable structure. */
  public void set(@Nullable NatsRequest request, String key, String value) {
    if (request == null || request.getHeaders() == null || request.getHeaders().isReadOnly()) {
      return;
    }

    request.getHeaders().put(key, value);
  }
}
