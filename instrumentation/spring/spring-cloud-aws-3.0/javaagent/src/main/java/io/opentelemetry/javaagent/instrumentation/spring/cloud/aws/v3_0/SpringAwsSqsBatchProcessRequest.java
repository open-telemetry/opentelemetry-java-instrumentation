/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import java.util.Collection;
import java.util.List;
import org.springframework.messaging.Message;

class SpringAwsSqsBatchProcessRequest {
  private final Collection<? extends Message<?>> messages;
  private final List<SqsMessage> sqsMessages;

  SpringAwsSqsBatchProcessRequest(
      Collection<? extends Message<?>> messages, List<SqsMessage> sqsMessages) {
    this.messages = messages;
    this.sqsMessages = sqsMessages;
  }

  Collection<? extends Message<?>> getMessages() {
    return messages;
  }

  // the sqs messages of the batch that carry tracing state
  List<SqsMessage> getSqsMessages() {
    return sqsMessages;
  }
}
