/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTracing;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;

// Holds singleton references.
public final class GrpcSingletons {
  private static final GrpcTracing TRACING =
      GrpcTracing.newBuilder(GlobalOpenTelemetry.get())
          .setCaptureExperimentalSpanAttributes(
              Config.get()
                  .getBooleanProperty(
                      "otel.instrumentation.grpc.experimental-span-attributes", false))
          .build();

  public static final ClientInterceptor CLIENT_INTERCEPTOR = TRACING.newClientInterceptor();

  public static final ServerInterceptor SERVER_INTERCEPTOR = TRACING.newServerInterceptor();

  public static final Context.Storage STORAGE = new ContextStorageBridge();
}
