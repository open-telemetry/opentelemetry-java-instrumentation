/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.internal.Initializer;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import java.net.Socket;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftRequestAccess {
  private static Access access;

  static {
    // initialize ThriftRequest class, so that Access can be set
    try {
      Class.forName(ThriftRequest.class.getName());
    } catch (ClassNotFoundException e) {
      // ignore
    }
  }

  static ThriftRequest newThriftRequest(
      @Nullable String methodName, @Nullable String serviceName, @Nullable Socket socket) {
    return access.newThriftRequest(methodName, serviceName, socket);
  }

  static ThriftRequest newThriftRequest(
      @Nullable String methodName,
      @Nullable String serviceName,
      @Nullable Socket socket,
      Map<String, String> headers) {
    return access.newThriftRequest(methodName, serviceName, socket, headers);
  }

  static void updateSocket(ThriftRequest request, @Nullable Socket socket) {
    access.updateSocket(request, socket);
  }

  @Initializer
  public static void setAccess(Access access) {
    ThriftRequestAccess.access = access;
  }

  private ThriftRequestAccess() {}

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public interface Access {

    ThriftRequest newThriftRequest(
        @Nullable String methodName, @Nullable String serviceName, @Nullable Socket socket);

    ThriftRequest newThriftRequest(
        @Nullable String methodName,
        @Nullable String serviceName,
        @Nullable Socket socket,
        Map<String, String> headers);

    void updateSocket(ThriftRequest request, @Nullable Socket socket);
  }
}
