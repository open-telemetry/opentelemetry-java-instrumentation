/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import java.util.List;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

class SpringAwsSqsBatchProcessRequest {
  private final int messageCount;
  private final List<SqsMessage> sqsMessages;
  private final ExecutionAttributes receiveRequest;

  SpringAwsSqsBatchProcessRequest(
      int messageCount, List<SqsMessage> sqsMessages, ExecutionAttributes receiveRequest) {
    // captured eagerly since the listener may modify the batch before processing completes
    this.messageCount = messageCount;
    this.sqsMessages = sqsMessages;
    this.receiveRequest = receiveRequest;
  }

  int getMessageCount() {
    return messageCount;
  }

  // the sqs messages of the batch that carry tracing state
  List<SqsMessage> getSqsMessages() {
    return sqsMessages;
  }

  // a receive request the batch came from; a batch is polled from a single queue, so every receive
  // request of the batch has the same queue url and endpoint
  ExecutionAttributes getReceiveRequest() {
    return receiveRequest;
  }
}
