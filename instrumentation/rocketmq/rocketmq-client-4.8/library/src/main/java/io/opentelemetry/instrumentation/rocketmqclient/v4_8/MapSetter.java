/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;

final class MapSetter implements TextMapSetter<SendMessageContext> {

  @Override
  public void set(@Nullable SendMessageContext carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getMessage().getProperties().put(key, value);
  }
}
