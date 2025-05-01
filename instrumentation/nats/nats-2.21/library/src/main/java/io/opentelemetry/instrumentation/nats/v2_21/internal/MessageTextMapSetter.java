/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.nats.client.Message;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum MessageTextMapSetter implements TextMapSetter<Message> {
  INSTANCE;

  @Override
  /* Can not work if getHeaders doesn't return a writable structure. */
  public void set(@Nullable Message carrier, String key, String value) {
    if (carrier == null) {
      return;
    }

    if (carrier.getHeaders() != null && !carrier.getHeaders().isReadOnly()) {
      carrier.getHeaders().put(key, value);
    }
  }
}
