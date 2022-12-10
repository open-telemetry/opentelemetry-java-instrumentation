/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v28.telemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.MessageMetadata;

enum ProducerAttributesExtractor implements AttributesExtractor<Message<?>, Attributes> {
  INSTANCE;

  private ProducerAttributesExtractor() {}

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, Message<?> message) {}

  @Override
  public void onEnd(
      AttributesBuilder attributesBuilder,
      Context context,
      Message<?> message,
      @Nullable Attributes attributes,
      @Nullable Throwable error) {
    if (null != attributes) {
      attributesBuilder.putAll(attributes);
    }

    if (message instanceof MessageImpl<?>) {
      MessageType type = MessageType.NORMAL;
      MessageImpl<?> impl = (MessageImpl<?>) message;
      MessageMetadata metadata = impl.getMessageBuilder();
      if (metadata.hasTxnidMostBits() || metadata.hasTxnidLeastBits()) {
        type = MessageType.TXN;
      } else if (metadata.hasDeliverAtTime()) {
        type = MessageType.DELAY;
      } else if (metadata.hasOrderingKey()) {
        type = MessageType.ORDER;
      } else if (metadata.hasChunkId()) {
        type = MessageType.CHUNK;
      }

      attributesBuilder.put(SemanticAttributes.MESSAGE_TYPE, type.name());
    }
  }

  enum MessageType {
    NORMAL,
    DELAY,
    TXN,
    ORDER,
    CHUNK
  }
}
