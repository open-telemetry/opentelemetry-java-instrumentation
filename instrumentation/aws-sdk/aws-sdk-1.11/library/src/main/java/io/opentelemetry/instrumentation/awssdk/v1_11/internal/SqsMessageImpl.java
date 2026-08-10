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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsMessageImpl implements SqsMessage {

  private final Message message;
  private final boolean sqsMessageCreateSpansEnabled;

  private SqsMessageImpl(Message message, boolean sqsMessageCreateSpansEnabled) {
    this.message = message;
    this.sqsMessageCreateSpansEnabled = sqsMessageCreateSpansEnabled;
  }

  static SqsMessage wrap(Message message, boolean sqsMessageCreateSpansEnabled) {
    return new SqsMessageImpl(message, sqsMessageCreateSpansEnabled);
  }

  static List<SqsMessage> wrap(List<Message> messages, boolean sqsMessageCreateSpansEnabled) {
    List<SqsMessage> result = new ArrayList<>();
    for (Message message : messages) {
      result.add(wrap(message, sqsMessageCreateSpansEnabled));
    }
    return result;
  }

  @Override
  public Context getCreationContext() {
    if (emitStableMessagingSemconv() && sqsMessageCreateSpansEnabled) {
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
  public String getMessageId() {
    return message.getMessageId();
  }
}
