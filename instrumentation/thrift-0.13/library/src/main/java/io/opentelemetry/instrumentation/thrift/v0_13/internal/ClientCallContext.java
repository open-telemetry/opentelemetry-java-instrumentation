/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientCallContext {
  private static final ThreadLocal<ClientCallContext> current = new ThreadLocal<>();

  @Nullable private ClientProtocolDecorator protocolDecorator;
  private boolean hasAsyncCallback;

  public static ClientCallContext start() {
    ClientCallContext context = new ClientCallContext();
    current.set(context);
    return context;
  }

  static void setClientProtocolDecorator(ClientProtocolDecorator protocolDecorator) {
    ClientCallContext context = current.get();
    if (context != null) {
      context.protocolDecorator = protocolDecorator;
    }
  }

  /** Returns {@code true} when a callback has been installed for ending the span. */
  static boolean hasAsyncCallback() {
    ClientCallContext context = current.get();
    return context != null && context.hasAsyncCallback;
  }

  /** Notify that a callback has been installed for ending the span. */
  public void setHasAsyncCallback() {
    this.hasAsyncCallback = true;
  }

  public void endSpan(@Nullable Throwable throwable) {
    if (protocolDecorator != null) {
      protocolDecorator.endSpan(throwable);
    }
  }

  public void end() {
    current.remove();
  }
}
