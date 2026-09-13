/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

/**
 * Tracks whether the current thread is inside an agent class file transformation.
 *
 * <p>Byte Buddy does not transform classes that are loaded while a transformation is already in
 * progress on the same thread - {@code AgentBuilder$Default$ExecutingTransformer#transform} returns
 * without transforming when the circularity lock cannot be acquired. Any class the agent causes to
 * be loaded from inside a transformation is therefore defined without instrumentation, permanently.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class InTransformation {

  // a depth rather than a flag - more than one transformer can be installed, and a class loaded
  // during a transformation is handed to all of them; using int[] avoids an extra thread local
  // lookup when updating the value
  private static final ThreadLocal<int[]> depth = ThreadLocal.withInitial(() -> new int[1]);

  private InTransformation() {}

  /** Returns whether the current thread is inside an agent class file transformation. */
  public static boolean get() {
    return depth.get()[0] > 0;
  }

  /**
   * WARNING This should not be used by instrumentation. It should only be used by {@code
   * io.opentelemetry.javaagent.tooling.bytebuddy.TransformationTrackingCircularityLock}.
   *
   * <p>The reason it can't be (easily) hidden is that this class needs to live in the bootstrap
   * class loader to be reachable both from the agent class loader and from instrumentation.
   */
  public static void enter() {
    depth.get()[0]++;
  }

  /** See {@link #enter()}. */
  public static void exit() {
    int[] current = depth.get();
    if (current[0] > 0) {
      current[0]--;
    }
  }
}
