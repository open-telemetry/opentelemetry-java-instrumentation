/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

/**
 * Represents type of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#operation-names">operations</a>
 * that may be used in a messaging system.
 */
public enum MessageOperation {
  SEND,
  RECEIVE,
  PROCESS;

  public String operationName() {
    return name().toLowerCase();
  }
}
