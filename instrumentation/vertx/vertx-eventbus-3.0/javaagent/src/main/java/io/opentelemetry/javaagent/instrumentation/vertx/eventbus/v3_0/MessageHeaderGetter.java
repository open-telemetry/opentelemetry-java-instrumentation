/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.vertx.core.MultiMap;
import javax.annotation.Nullable;

enum MessageHeaderGetter implements TextMapGetter<EventBusRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(EventBusRequest carrier) {
    MultiMap headers = carrier.headers();
    return headers != null ? headers.names() : emptyList();
  }

  @Nullable
  @Override
  public String get(@Nullable EventBusRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    MultiMap headers = carrier.headers();
    if (headers == null) {
      return null;
    }
    return headers.get(key);
  }
}
