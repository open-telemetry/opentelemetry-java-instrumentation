/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import javax.annotation.Nullable;

public final class ApplicationCallbackHandle {

  @Nullable private Runnable removeAgentCallback;
  @Nullable private Runnable releaseAnchor;

  public ApplicationCallbackHandle(Runnable removeAgentCallback, Runnable releaseAnchor) {
    this.removeAgentCallback = removeAgentCallback;
    this.releaseAnchor = releaseAnchor;
  }

  public void close() {
    Runnable removeAgentCallback;
    Runnable releaseAnchor;
    synchronized (this) {
      removeAgentCallback = this.removeAgentCallback;
      if (removeAgentCallback == null) {
        return;
      }
      this.removeAgentCallback = null;
      releaseAnchor = this.releaseAnchor;
      this.releaseAnchor = null;
    }

    try {
      removeAgentCallback.run();
    } finally {
      if (releaseAnchor != null) {
        releaseAnchor.run();
      }
    }
  }
}
