/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.eventbus.v3_0;

import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.Message;
import javax.annotation.Nullable;

final class EventBusRequest {

  private final String address;
  @Nullable private final MultiMap headers;

  private EventBusRequest(String address, @Nullable MultiMap headers) {
    this.address = address;
    this.headers = headers;
  }

  static EventBusRequest forProducer(String address, @Nullable MultiMap headers) {
    return new EventBusRequest(address, headers);
  }

  static EventBusRequest forConsumer(Message<?> message) {
    return new EventBusRequest(message.address(), message.headers());
  }

  String getAddress() {
    return address;
  }

  @Nullable
  MultiMap headers() {
    return headers;
  }
}
