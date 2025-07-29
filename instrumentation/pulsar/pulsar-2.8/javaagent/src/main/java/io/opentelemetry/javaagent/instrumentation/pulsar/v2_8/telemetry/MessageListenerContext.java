/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

/**
 * Helper class used to determine whether message is going to be processed by a listener. If we know
 * that message is going to be passed to a message listener, that would produce a span for the
 * "process" operation, we are going to suppress the span from the message "receive" operation.
 */
public final class MessageListenerContext {
  private static final ThreadLocal<Boolean> processing = new ThreadLocal<>();

  private MessageListenerContext() {}

  /** Call on entry to a method that will pass the received message to a message listener. */
  public static void startProcessing() {
    processing.set(true);
  }

  public static void endProcessing() {
    processing.remove();
  }

  /** Returns true if we expect a received message to be passed to a listener. */
  public static boolean isProcessing() {
    return processing.get() != null;
  }
}
