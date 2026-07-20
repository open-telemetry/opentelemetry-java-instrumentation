/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

final class TextMapExtractAdapter implements TextMapGetter<RocketMqConsumerRequest> {

  @Override
  public Iterable<String> keys(RocketMqConsumerRequest carrier) {
    return carrier.getMessage().getProperties().keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable RocketMqConsumerRequest carrier, String key) {
    return carrier == null ? null : carrier.getMessage().getProperties().get(key);
  }
}
