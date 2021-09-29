/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTracing;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetAttributesExtractor;

// Holds singleton references.
public final class GrpcSingletons {

  public static final ClientInterceptor CLIENT_INTERCEPTOR;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  public static final Context.Storage STORAGE = new ContextStorageBridge();

  static {
    boolean experimentalSpanAttributes =
        Config.get().getBoolean("otel.instrumentation.grpc.experimental-span-attributes", false);
    PeerServiceAttributesExtractor<GrpcRequest, Status> peerServiceAttributesExtractor =
        PeerServiceAttributesExtractor.create(new GrpcNetAttributesExtractor());

    GrpcTracing tracing =
        GrpcTracing.newBuilder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(experimentalSpanAttributes)
            .addAttributeExtractor(peerServiceAttributesExtractor)
            .build();

    CLIENT_INTERCEPTOR = tracing.newClientInterceptor();
    SERVER_INTERCEPTOR = tracing.newServerInterceptor();
  }

  private GrpcSingletons() {}
}
