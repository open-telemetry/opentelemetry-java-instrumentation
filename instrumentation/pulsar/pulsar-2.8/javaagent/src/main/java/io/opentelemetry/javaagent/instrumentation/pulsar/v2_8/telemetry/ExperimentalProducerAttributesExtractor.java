/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Locale;
import javax.annotation.Nullable;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.impl.MessageImpl;
import org.apache.pulsar.common.api.proto.MessageMetadata;

enum ExperimentalProducerAttributesExtractor implements AttributesExtractor<PulsarRequest, Void> {
  INSTANCE;

  private static final AttributeKey<String> MESSAGE_TYPE =
      AttributeKey.stringKey("messaging.pulsar.message.type");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, PulsarRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributesBuilder,
      Context context,
      PulsarRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {
    Message<?> message = request.getMessage();
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

      attributesBuilder.put(MESSAGE_TYPE, type.name().toLowerCase(Locale.ROOT));
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
