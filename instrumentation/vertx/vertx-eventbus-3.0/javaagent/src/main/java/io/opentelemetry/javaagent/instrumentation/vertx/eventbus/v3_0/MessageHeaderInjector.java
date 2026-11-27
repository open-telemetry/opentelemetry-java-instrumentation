/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.eventbus.Message;
import javax.annotation.Nullable;

enum MessageHeaderInjector implements TextMapSetter<Message<?>> {
  INSTANCE;

  @Override
  public void set(@Nullable Message<?> carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    // Message.headers() lazily creates a non-null MultiMap on MessageImpl across
    // all Vert.x versions (3.0+), so it is always safe to write here. The application may have
    // supplied the same header, so overwrite its value instead of appending to it.
    carrier.headers().set(key, value);
  }
}
