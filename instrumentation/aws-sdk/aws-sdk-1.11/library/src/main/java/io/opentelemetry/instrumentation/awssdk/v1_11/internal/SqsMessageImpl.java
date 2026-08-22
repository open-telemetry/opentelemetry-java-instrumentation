/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsMessageImpl implements SqsMessage {

  private final Message message;

  private SqsMessageImpl(Message message) {
    this.message = message;
  }

  static SqsMessage wrap(Message message) {
    return new SqsMessageImpl(message);
  }

  static List<SqsMessage> wrap(List<Message> messages) {
    List<SqsMessage> result = new ArrayList<>();
    for (Message message : messages) {
      result.add(wrap(message));
    }
    return result;
  }

  @Override
  public Context getCreationContext() {
    if (emitStableMessagingSemconv()) {
      Context messageAttributeContext =
          SqsParentContext.ofMessageAttributes(toStringMap(message.getMessageAttributes()));
      if (Span.fromContext(messageAttributeContext).getSpanContext().isValid()) {
        return messageAttributeContext;
      }
    }
    return SqsParentContext.ofSystemAttributes(message.getAttributes());
  }

  private static Map<String, String> toStringMap(
      Map<String, MessageAttributeValue> messageAttributes) {
    Map<String, String> result = new HashMap<>();
    messageAttributes.forEach((key, value) -> result.put(key, value.getStringValue()));
    return result;
  }

  @Override
  public Map<String, String> getAttributes() {
    return message.getAttributes();
  }

  @Override
  @Nullable
  public String getMessageAttribute(String name) {
    MessageAttributeValue value = message.getMessageAttributes().get(name);
    return value != null ? value.getStringValue() : null;
  }

  @Override
  public Collection<String> getMessageAttributeNames() {
    // the message is owned by the caller, so its attribute names are snapshotted
    return new ArrayList<>(message.getMessageAttributes().keySet());
  }

  @Override
  public String getMessageId() {
    return message.getMessageId();
  }
}
