/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerCallContext {
  private static final ScopedThreadValue<ServerCallContext> current = new ScopedThreadValue<>();

  private final TTransport transport;
  @Nullable private ServerCallContext previous;

  private ServerCallContext(TTransport transport) {
    this.transport = transport;
  }

  public static ServerCallContext start(TTransport transport) {
    ServerCallContext context = new ServerCallContext(transport);
    context.previous = current.set(context);
    return context;
  }

  @Nullable
  public static TTransport getTransport() {
    ServerCallContext context = current.get();
    return context != null ? context.transport : null;
  }

  public void end() {
    current.restore(previous);
  }
}
