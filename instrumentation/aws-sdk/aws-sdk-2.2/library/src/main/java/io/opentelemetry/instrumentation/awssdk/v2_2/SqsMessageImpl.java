/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import java.util.Map;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

final class SqsMessageImpl implements SqsMessage {

  private final Message message;

  private SqsMessageImpl(Message message) {
    this.message = message;
  }

  static SqsMessage wrap(Message message) {
    return new SqsMessageImpl(message);
  }

  @Override
  public Map<String, MessageAttributeValue> messageAttributes() {
    return message.messageAttributes();
  }

  @Override
  public Map<String, String> attributesAsStrings() {
    return message.attributesAsStrings();
  }
}
