/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsMessageImpl implements SqsMessage {

  private final Message message;

  private SqsMessageImpl(Message message) {
    this.message = message;
  }

  public static SqsMessage wrap(Message message) {
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
  public Map<String, MessageAttributeValue> messageAttributes() {
    return message.messageAttributes();
  }

  @Override
  public Map<String, String> attributesAsStrings() {
    return message.attributesAsStrings();
  }

  @Override
  public String getMessageAttribute(String name) {
    MessageAttributeValue value = message.messageAttributes().get(name);
    return value != null ? value.stringValue() : null;
  }

  @Override
  public String getMessageId() {
    return message.messageId();
  }
}
