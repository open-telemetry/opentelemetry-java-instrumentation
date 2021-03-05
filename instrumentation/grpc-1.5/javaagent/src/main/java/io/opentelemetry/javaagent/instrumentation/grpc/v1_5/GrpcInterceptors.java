/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_5;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.grpc.v1_5.GrpcTracing;

// Holds singleton references to tracers.
public final class GrpcInterceptors {
  private static final GrpcTracing TRACING =
      GrpcTracing.newBuilder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBooleanProperty(
                      "otel.instrumentation.grpc.experimental-span-attributes", false))
          .build();

  public static final ClientInterceptor CLIENT_INTERCEPTOR = TRACING.newClientInterceptor();

  public static final ServerInterceptor SERVER_INTERCEPTOR = TRACING.newServerInterceptor();
}
