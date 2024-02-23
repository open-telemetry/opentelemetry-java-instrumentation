/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

/**
 * Helper class for suppressing spans from the message "receive" operation when we know that the
 * received message will be passed to a message listener that would produce a span for the "process"
 * operation.
 */
public final class MessageListenerContext {
  private static final ThreadLocal<Boolean> active = new ThreadLocal<>();

  private MessageListenerContext() {}

  /** Call on entry to a method that will pass the received message to a message listener. */
  public static void enter() {
    active.set(Boolean.TRUE);
  }

  public static void exit() {
    active.remove();
  }

  /** Returns true if we expect a received message to be passed to a listener. */
  public static boolean isActive() {
    return active.get() != null;
  }
}
