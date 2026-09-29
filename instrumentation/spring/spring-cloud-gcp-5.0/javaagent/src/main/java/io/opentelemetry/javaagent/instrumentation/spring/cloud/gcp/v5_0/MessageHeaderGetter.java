/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.gcp.v5_0;

import com.google.cloud.spring.pubsub.support.converter.ConvertedBasicAcknowledgeablePubsubMessage;
import io.opentelemetry.context.propagation.TextMapGetter;
import javax.annotation.Nullable;

final class MessageHeaderGetter
    implements TextMapGetter<ConvertedBasicAcknowledgeablePubsubMessage<?>> {
  @Override
  public Iterable<String> keys(ConvertedBasicAcknowledgeablePubsubMessage<?> carrier) {
    return carrier.getPubsubMessage().getAttributesMap().keySet();
  }

  @Override
  @Nullable
  public String get(@Nullable ConvertedBasicAcknowledgeablePubsubMessage<?> carrier, String key) {
    return carrier == null ? null : carrier.getPubsubMessage().getAttributesMap().get(key);
  }
}
