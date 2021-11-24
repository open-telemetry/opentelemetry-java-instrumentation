/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.rocketmq.client.hook.SendMessageContext;

enum MapSetter implements TextMapSetter<SendMessageContext> {
  INSTANCE;

  @Override
  public void set(SendMessageContext carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getMessage().getProperties().put(key, value);
  }
}
