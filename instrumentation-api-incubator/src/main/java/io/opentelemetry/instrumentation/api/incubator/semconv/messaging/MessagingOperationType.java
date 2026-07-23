/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

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
  private final String defaultOperationName;

  MessagingOperationType(String value, String defaultOperationName) {
    this.value = value;
    this.defaultOperationName = defaultOperationName;
  }

  String value() {
    return value;
  }

  String defaultOperationName() {
    return defaultOperationName;
  }
}
