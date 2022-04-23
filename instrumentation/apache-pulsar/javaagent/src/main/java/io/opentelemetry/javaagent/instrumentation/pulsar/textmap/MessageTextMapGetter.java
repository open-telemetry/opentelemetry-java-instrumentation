/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.textmap;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.apache.pulsar.client.impl.MessageImpl;

public final class MessageTextMapGetter implements TextMapGetter<MessageImpl<?>> {
  public static final TextMapGetter<MessageImpl<?>> INSTANCE = new MessageTextMapGetter();


  @Override
  public Iterable<String> keys(MessageImpl<?> message) {
    return message.getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable MessageImpl<?> message, String key) {
    return null == message ? null : message.getProperties().get(key);
  }
}
