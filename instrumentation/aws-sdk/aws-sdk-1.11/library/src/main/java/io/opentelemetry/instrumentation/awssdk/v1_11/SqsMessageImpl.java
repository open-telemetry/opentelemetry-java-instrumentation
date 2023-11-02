/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.services.sqs.model.Message;
import java.util.Map;

final class SqsMessageImpl implements SqsMessage {

  private final Message message;

  private SqsMessageImpl(Message message) {
    this.message = message;
  }

  static SqsMessage wrap(Message message) {
    return new SqsMessageImpl(message);
  }

  @Override
  public Map<String, String> getAttributes() {
    return message.getAttributes();
  }
}
