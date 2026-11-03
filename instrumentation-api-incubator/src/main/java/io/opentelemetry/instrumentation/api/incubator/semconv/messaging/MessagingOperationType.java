/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import javax.annotation.Nullable;

/**
 * Represents a <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#operation-types">messaging
 * operation type</a>.
 */
public enum MessagingOperationType {
  CREATE("create"),
  SEND("send"),
  RECEIVE("receive"),
  PROCESS("process"),
  SETTLE("settle");

  private final String value;

  MessagingOperationType(String value) {
    this.value = value;
  }

  String value() {
    return value;
  }

  /**
   * Returns the operation type with the given {@code messaging.operation.type} value, or {@code
   * null} when no operation type uses it.
   */
  @Nullable
  static MessagingOperationType fromValue(@Nullable String value) {
    for (MessagingOperationType operationType : values()) {
      if (operationType.value.equals(value)) {
        return operationType;
      }
    }
    return null;
  }
}
