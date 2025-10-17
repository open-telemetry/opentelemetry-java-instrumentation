/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common;

import com.google.auto.value.AutoValue;
import java.net.Socket;
import java.util.Map;
import javax.annotation.Nullable;

@AutoValue
public abstract class ThriftRequest {

  public static ThriftRequest create(
      @Nullable String serviceName,
      String methodName,
      @Nullable Socket socket,
      Map<String, String> header) {
    return new AutoValue_ThriftRequest(serviceName, methodName, socket, header);
  }

  @Nullable
  public abstract String getServiceName();

  public abstract String getMethodName();

  @Nullable
  public abstract Socket getSocket();

  public abstract Map<String, String> getHeader();
}
