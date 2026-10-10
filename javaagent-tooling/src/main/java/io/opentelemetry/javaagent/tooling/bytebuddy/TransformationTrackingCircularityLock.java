/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy;

import io.opentelemetry.javaagent.bootstrap.internal.InTransformation;
import net.bytebuddy.agent.builder.AgentBuilder;

/**
 * A {@link AgentBuilder.CircularityLock} that additionally records, in {@link InTransformation},
 * that the current thread is inside a class file transformation.
 *
 * <p>The lock is acquired before type matching starts and released once the transformation
 * completes, so it marks exactly the window in which the agent must not call into application code:
 * anything the agent loads in that window is defined without instrumentation.
 */
public final class TransformationTrackingCircularityLock implements AgentBuilder.CircularityLock {

  private final AgentBuilder.CircularityLock delegate = new AgentBuilder.CircularityLock.Default();

  @Override
  public boolean acquire() {
    if (delegate.acquire()) {
      InTransformation.enter();
      return true;
    }
    // the lock is already held by this thread, so we are already inside a transformation
    return false;
  }

  @Override
  public void release() {
    InTransformation.exit();
    delegate.release();
  }
}
