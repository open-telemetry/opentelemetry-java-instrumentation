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
  CREATE("create", "create"),
  SEND("send", "publish"),
  RECEIVE("receive", "receive"),
  PROCESS("process", "process"),
  SETTLE("settle", "settle");

  private final String value;
  private final String legacyOperationName;

  MessagingOperationType(String value, String legacyOperationName) {
    this.value = value;
    this.legacyOperationName = legacyOperationName;
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

  /**
   * Returns the operation name used by the old messaging semantic conventions, i.e. the value of
   * the {@code messaging.operation} attribute and the operation part of the old span name. The
   * v1.43 conventions require a system-specific operation name, which callers have to provide
   * explicitly.
   */
  String legacyOperationName() {
    return legacyOperationName;
  }
}
