/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

enum MessageMapGetter implements TextMapGetter<MessageView> {
  INSTANCE;

  @Override
  public Iterable<String> keys(MessageView carrier) {
    return carrier.getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable MessageView carrier, String key) {
    return carrier == null ? null : carrier.getProperties().get(key);
  }
}
