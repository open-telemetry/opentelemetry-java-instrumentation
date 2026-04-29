/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import static java.util.Collections.emptyMap;

import io.opentelemetry.instrumentation.thrift.v0_13.internal.ThriftRequestAccess;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import javax.annotation.Nullable;

public final class ThriftRequest {

  @Nullable private final String methodName;
  @Nullable private final String serviceName;
  private final Map<String, String> headers;
  @Nullable private SocketAddress localAddress;
  @Nullable private SocketAddress remoteAddress;

  static {
    ThriftRequestAccess.setAccess(
        new ThriftRequestAccess.Access() {
          @Override
          public ThriftRequest newThriftRequest(
              @Nullable String methodName, @Nullable String serviceName, @Nullable Socket socket) {
            return new ThriftRequest(methodName, serviceName, socket);
          }

          @Override
          public ThriftRequest newThriftRequest(
              @Nullable String methodName,
              @Nullable String serviceName,
              @Nullable Socket socket,
              Map<String, String> headers) {
            return new ThriftRequest(methodName, serviceName, socket, headers);
          }

          @Override
          public void updateSocket(ThriftRequest request, @Nullable Socket socket) {
            request.updateSocket(socket);
          }
        });
  }

  ThriftRequest(
      @Nullable String methodName, @Nullable String serviceName, @Nullable Socket socket) {
    this(methodName, serviceName, socket, emptyMap());
  }

  ThriftRequest(
      @Nullable String methodName,
      @Nullable String serviceName,
      @Nullable Socket socket,
      Map<String, String> headers) {
    this.methodName = methodName;
    this.serviceName = serviceName;
    this.headers = headers;
    updateSocket(socket);
  }

  void updateSocket(@Nullable Socket socket) {
    if (socket != null) {
      this.localAddress = socket.getLocalSocketAddress();
      this.remoteAddress = socket.getRemoteSocketAddress();
    }
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
