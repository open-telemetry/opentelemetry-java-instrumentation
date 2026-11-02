/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.internal;

import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * The circularity lock shared by the early and main agent transformers.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class AgentCircularityLock extends AgentBuilder.CircularityLock.Default {

  public static final AgentCircularityLock INSTANCE = new AgentCircularityLock();

  private AgentCircularityLock() {}

  /**
   * Returns whether the current thread owns the circularity lock. Byte Buddy also holds this lock
   * during transformer installation, so this is not an exact indication of class transformation.
   */
  public boolean isHeldByCurrentThread() {
    return isLocked();
  }
}
