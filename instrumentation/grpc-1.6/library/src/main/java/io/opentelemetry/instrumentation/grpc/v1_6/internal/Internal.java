/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.ClientInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Internal {

  private static volatile BiFunction<GrpcTelemetry, String, ClientInterceptor>
      clientInterceptorFactory;

  public static void setClientInterceptorFactory(
      BiFunction<GrpcTelemetry, String, ClientInterceptor> factory) {
    clientInterceptorFactory = factory;
  }

  public static ClientInterceptor createClientInterceptor(
      GrpcTelemetry telemetry, @Nullable String target) {
    return clientInterceptorFactory.apply(telemetry, target);
  }

  private Internal() {}
}
