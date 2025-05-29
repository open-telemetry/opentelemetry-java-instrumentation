/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
  public Map<String, String> getAttributes() {
    return message.getAttributes();
  }

  @Override
  public String getMessageAttribute(String name) {
    MessageAttributeValue value = message.getMessageAttributes().get(name);
    return value != null ? value.getStringValue() : null;
  }

  @Override
  public String getMessageId() {
    return message.getMessageId();
  }
}
