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
  @Nullable private final Message<?> message;

  private EventBusRequest(String address, @Nullable Message<?> message) {
    this.address = address;
    this.message = message;
  }

  static EventBusRequest forProducer(String address) {
    return new EventBusRequest(address, null);
  }

  static EventBusRequest forConsumer(Message<?> message) {
    return new EventBusRequest(message.address(), message);
  }

  String getAddress() {
    return address;
  }

  @Nullable
  MultiMap headers() {
    return message != null ? message.headers() : null;
  }
}
