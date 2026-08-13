/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

/**
 * Tracks when a messaging receive is initiated by an internal listener polling loop.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class InternalListenerPollContext {

  private static final ThreadLocal<Boolean> internalListenerPoll = new ThreadLocal<>();

  public static boolean enter() {
    boolean wasActive = isActive();
    internalListenerPoll.set(true);
    return wasActive;
  }

  public static void exit(boolean wasActive) {
    if (wasActive) {
      internalListenerPoll.set(true);
    } else {
      internalListenerPoll.remove();
    }
  }

  public static boolean isActive() {
    return internalListenerPoll.get() != null;
  }

  private InternalListenerPollContext() {}
}
