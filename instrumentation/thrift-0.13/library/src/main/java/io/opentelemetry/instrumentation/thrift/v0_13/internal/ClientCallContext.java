/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import static java.util.Collections.emptyMap;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClientCallContext {
  private static final ThreadLocal<ClientCallContext> current = new ThreadLocal<>();

  private final Instrumenter<ThriftRequest, ThriftResponse> instrumenter;
  @Nullable public ThriftRequest request;
  @Nullable public Context context;
  boolean contextPropagated;

  public static ClientCallContext start(
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
      String name,
      Class<?> clientClass,
      @Nullable Socket socket) {
    return start(
        instrumenter,
        name,
        clientClass,
        socket != null ? socket.getLocalSocketAddress() : null,
        socket != null ? socket.getRemoteSocketAddress() : null);
  }

  public static ClientCallContext start(
      Instrumenter<ThriftRequest, ThriftResponse> instrumenter,
      String name,
      Class<?> clientClass,
      @Nullable SocketAddress localAddress,
      @Nullable SocketAddress remoteAddress) {
    ClientCallContext callContext = new ClientCallContext(instrumenter);
    current.set(callContext);

    Context parentContext = Context.current();
    ThriftRequest request =
        new ThriftRequest(
            name, thriftServiceName(clientClass), localAddress, remoteAddress, emptyMap());
    if (instrumenter.shouldStart(parentContext, request)) {
      callContext.request = request;
      callContext.context = instrumenter.start(parentContext, request);
    }

    return callContext;
  }

  /**
   * Returns the enclosing thrift service class name (e.g. {@code com.example.MyService}) for an
   * inner client class such as {@code com.example.MyService$Client}; falls back to the class itself
   * when there is no enclosing class.
   */
  private static String thriftServiceName(Class<?> clientClass) {
    Class<?> declaring = clientClass.getDeclaringClass();
    return declaring != null ? declaring.getName() : clientClass.getName();
  }

  private ClientCallContext(Instrumenter<ThriftRequest, ThriftResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Nullable
  public static ClientCallContext get() {
    return current.get();
  }

  public void endSpan(@Nullable Throwable throwable) {
    if (context != null && request != null) {
      instrumenter.end(context, request, null, throwable);
    }
  }

  public void close() {
    current.remove();
  }
}
