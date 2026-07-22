/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

/**
 * Represents an operation that may be used in a messaging system.
 *
 * @deprecated Use {@link MessagingOperationType}. Will be removed in 3.0.
 */
@Deprecated // to be removed in 3.0
public enum MessageOperation {
  PUBLISH(MessagingOperationType.SEND),
  RECEIVE(MessagingOperationType.RECEIVE),
  PROCESS(MessagingOperationType.PROCESS);

  private final MessagingOperationType operationType;

  MessageOperation(MessagingOperationType operationType) {
    this.operationType = operationType;
  }

  MessagingOperationType type() {
    return operationType;
  }
}
