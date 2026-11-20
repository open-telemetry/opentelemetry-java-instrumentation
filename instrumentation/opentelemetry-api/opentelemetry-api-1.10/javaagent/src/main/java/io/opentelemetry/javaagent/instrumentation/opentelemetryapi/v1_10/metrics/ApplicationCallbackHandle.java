/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import javax.annotation.Nullable;

public final class ApplicationCallbackHandle {

  @Nullable private Runnable closeCallback;
  @Nullable private Runnable onClose;

  public ApplicationCallbackHandle(Runnable closeCallback, Runnable onClose) {
    this.closeCallback = closeCallback;
    this.onClose = onClose;
  }

  public void close() {
    Runnable closeCallback;
    Runnable onClose;
    synchronized (this) {
      closeCallback = this.closeCallback;
      if (closeCallback == null) {
        return;
      }
      this.closeCallback = null;
      onClose = this.onClose;
      this.onClose = null;
    }

    try {
      closeCallback.run();
    } finally {
      if (onClose != null) {
        onClose.run();
      }
    }
  }
}
