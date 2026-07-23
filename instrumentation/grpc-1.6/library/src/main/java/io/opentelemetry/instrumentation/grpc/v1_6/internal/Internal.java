/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.Objects.requireNonNull;

import io.grpc.ServerInterceptor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class Internal {

  @Nullable
  private static volatile Function<GrpcTelemetry, ServerInterceptor> serverInterceptorFactory;

  public static void setServerInterceptorFactory(
      Function<GrpcTelemetry, ServerInterceptor> factory) {
    serverInterceptorFactory = factory;
  }

  public static ServerInterceptor createServerInterceptor(GrpcTelemetry telemetry) {
    // serverInterceptorFactory is guaranteed non-null because GrpcTelemetry registers it during
    // static initialization, before a GrpcTelemetry instance can be passed here
    return requireNonNull(serverInterceptorFactory, "serverInterceptorFactory").apply(telemetry);
  }

  private Internal() {}
}
