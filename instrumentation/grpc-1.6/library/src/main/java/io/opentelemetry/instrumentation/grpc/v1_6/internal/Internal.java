/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.ClientInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.util.function.BiFunction;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class Internal {

  private static volatile BiFunction<GrpcTelemetry, String, ClientInterceptor>
      clientInterceptorFactory;

  public static void setClientInterceptorFactory(
      BiFunction<GrpcTelemetry, String, ClientInterceptor> factory) {
    clientInterceptorFactory = factory;
  }

  /** Creates an interceptor using the target already resolved by gRPC's channel builder. */
  public static ClientInterceptor createClientInterceptor(GrpcTelemetry telemetry, String target) {
    return clientInterceptorFactory.apply(telemetry, target);
  }

  private Internal() {}
}
