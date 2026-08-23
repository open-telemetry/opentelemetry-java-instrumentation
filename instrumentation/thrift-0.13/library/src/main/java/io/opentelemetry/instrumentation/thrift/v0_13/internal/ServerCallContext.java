/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import javax.annotation.Nullable;
import org.apache.thrift.transport.TTransport;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServerCallContext {
  private static final ThreadLocal<ServerCallContext> current = new ThreadLocal<>();

  private final TTransport transport;

  private ServerCallContext(TTransport transport) {
    this.transport = transport;
  }

  public static ServerCallContext start(TTransport transport) {
    ServerCallContext context = new ServerCallContext(transport);
    current.set(context);
    return context;
  }

  @Nullable
  public static TTransport getTransport() {
    ServerCallContext context = current.get();
    return context != null ? context.transport : null;
  }

  public void end() {
    current.remove();
  }
}
