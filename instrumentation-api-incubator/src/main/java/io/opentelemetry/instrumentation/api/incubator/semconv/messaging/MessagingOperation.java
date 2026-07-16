/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nullable;

final class MessagingOperation {

  static MessagingOperation create(MessageOperation operation) {
    return create(operation, operation.operationName());
  }

  static MessagingOperation create(MessageOperation operation, String name) {
    return new MessagingOperation(operation, name);
  }

  @Nullable
  static MessagingOperation createNullable(@Nullable MessageOperation operation) {
    return operation == null ? null : create(operation, operation.operationName());
  }

  private final MessageOperation operation;
  private final String name;

  private MessagingOperation(MessageOperation operation, String name) {
    this.operation = requireNonNull(operation, "operation");
    this.name = requireNonNull(name, "name");
  }

  MessageOperation operation() {
    return operation;
  }

  String name() {
    return name;
  }

  String type() {
    switch (operation) {
      case PUBLISH:
        return "send";
      case RECEIVE:
        return "receive";
      case PROCESS:
        return "process";
    }
    throw new IllegalStateException("Can't possibly happen");
  }
}
