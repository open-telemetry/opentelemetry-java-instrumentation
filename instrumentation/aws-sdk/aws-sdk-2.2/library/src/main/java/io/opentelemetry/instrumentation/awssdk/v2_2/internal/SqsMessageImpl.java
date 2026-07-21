/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import io.opentelemetry.context.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsMessageImpl implements SqsMessage {

  private final Message message;
  @Nullable private final TracingExecutionInterceptor config;

  private SqsMessageImpl(Message message) {
    this.message = message;
    this.config = null;
  }

  private SqsMessageImpl(Message message, TracingExecutionInterceptor config) {
    this.message = message;
    this.config = config;
  }

  public static SqsMessage wrap(Message message) {
    return new SqsMessageImpl(message);
  }

  public static SqsMessage wrap(Message message, TracingExecutionInterceptor config) {
    return new SqsMessageImpl(message, config);
  }

  static List<SqsMessage> wrap(List<Message> messages, TracingExecutionInterceptor config) {
    List<SqsMessage> result = new ArrayList<>();
    for (Message message : messages) {
      result.add(wrap(message, config));
    }
    return result;
  }

  @Override
  public Context getCreationContext() {
    return config != null ? SqsParentContext.ofMessage(this, config) : Context.root();
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
