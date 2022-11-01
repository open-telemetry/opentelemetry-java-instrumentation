/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

// Holds singleton references.
public final class GrpcSingletons {

  public static final ClientInterceptor CLIENT_INTERCEPTOR;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  public static final Context.Storage STORAGE = new ContextStorageBridge(false);

  static {
    boolean experimentalSpanAttributes =
        InstrumentationConfig.get()
            .getBoolean("otel.instrumentation.grpc.experimental-span-attributes", false);

    GrpcTelemetry telemetry =
        GrpcTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(experimentalSpanAttributes)
            .setRequestMetadataValuesToCapture(CommonConfig.get().getRpcRequestMetadata())
            .build();

    CLIENT_INTERCEPTOR = telemetry.newClientInterceptor();
    SERVER_INTERCEPTOR = telemetry.newServerInterceptor();
  }

  private GrpcSingletons() {}
}
