/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapSetter;
import org.apache.rocketmq.client.hook.SendMessageContext;

final class TextMapInjectAdapter implements TextMapSetter<SendMessageContext> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(SendMessageContext carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getMessage().getProperties().put(key, value);
  }
}
