/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.Message;

final class MessageExtractAdapter implements TextMapGetter<Message> {

  @Override
  public Iterable<String> keys(Message carrier) {
    return carrier.getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable Message carrier, String key) {
    return carrier == null ? null : carrier.getProperties().get(key);
  }
}
