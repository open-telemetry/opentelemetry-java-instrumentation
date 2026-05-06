/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static java.util.Collections.emptyMap;

import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import javax.annotation.Nullable;

public final class ThriftRequest {

  @Nullable private final String methodName;
  @Nullable private final String serviceName;
  private final Map<String, String> headers;
  @Nullable private final SocketAddress localAddress;
  @Nullable private final SocketAddress remoteAddress;

  public ThriftRequest(
      @Nullable String methodName, @Nullable String serviceName, @Nullable Socket socket) {
    this(methodName, serviceName, socket, emptyMap());
  }

  public ThriftRequest(
      @Nullable String methodName,
      @Nullable String serviceName,
      @Nullable Socket socket,
      Map<String, String> headers) {
    this(
        methodName,
        serviceName,
        socket != null ? socket.getLocalSocketAddress() : null,
        socket != null ? socket.getRemoteSocketAddress() : null,
        headers);
  }

  public ThriftRequest(
      @Nullable String methodName,
      @Nullable String serviceName,
      @Nullable SocketAddress localAddress,
      @Nullable SocketAddress remoteAddress,
      Map<String, String> headers) {
    this.methodName = methodName;
    this.serviceName = serviceName;
    this.headers = headers;
    this.localAddress = localAddress;
    this.remoteAddress = remoteAddress;
  }

  @Nullable
  public String getMethodName() {
    return methodName;
  }

  @Nullable
  public String getServiceName() {
    return serviceName;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  @Nullable
  public SocketAddress getLocalAddress() {
    return localAddress;
  }

  @Nullable
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }
}
