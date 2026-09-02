/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

/**
 * An individual telemetry signal that a messaging instrumentation can emit for one messaging
 * operation.
 *
 * <p>Signals are tracked separately from each other so that a layer can own some of the telemetry
 * for an operation without owning the rest of it. Owning the process span, for example, says
 * nothing about who counts the delivered messages.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public enum MessagingTelemetrySignal {
  /** The span describing the operation. */
  SPAN(1),
  /** The {@code messaging.client.operation.duration} histogram. */
  CLIENT_OPERATION_DURATION(1 << 1),
  /** The {@code messaging.process.duration} histogram. */
  PROCESS_DURATION(1 << 2),
  /** The {@code messaging.client.sent.messages} counter. */
  SENT_MESSAGES(1 << 3),
  /** The {@code messaging.client.consumed.messages} counter. */
  CONSUMED_MESSAGES(1 << 4);

  private final int bit;

  MessagingTelemetrySignal(int bit) {
    this.bit = bit;
  }

  int bit() {
    return bit;
  }
}
