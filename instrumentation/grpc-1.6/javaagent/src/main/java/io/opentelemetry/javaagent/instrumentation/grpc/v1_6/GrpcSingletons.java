/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static java.util.Collections.emptyList;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// Holds singleton references.
public final class GrpcSingletons {

  public static final ClientInterceptor CLIENT_INTERCEPTOR;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  private static final AtomicReference<Context.Storage> STORAGE_REFERENCE = new AtomicReference<>();

  static {
    boolean experimentalSpanAttributes =
        AgentInstrumentationConfig.get()
            .getBoolean("otel.instrumentation.grpc.experimental-span-attributes", false);

    List<String> clientRequestMetadata =
        AgentInstrumentationConfig.get()
            .getList("otel.instrumentation.grpc.capture-metadata.client.request", emptyList());
    List<String> serverRequestMetadata =
        AgentInstrumentationConfig.get()
            .getList("otel.instrumentation.grpc.capture-metadata.server.request", emptyList());

    GrpcTelemetry telemetry =
        GrpcTelemetry.builder(GlobalOpenTelemetry.get())
            .setCaptureExperimentalSpanAttributes(experimentalSpanAttributes)
            .setCapturedClientRequestMetadata(clientRequestMetadata)
            .setCapturedServerRequestMetadata(serverRequestMetadata)
            .build();

    CLIENT_INTERCEPTOR = telemetry.newClientInterceptor();
    SERVER_INTERCEPTOR = telemetry.newServerInterceptor();
  }

  public static Context.Storage getStorage() {
    return STORAGE_REFERENCE.get();
  }

  public static Context.Storage setStorage(Context.Storage storage) {
    STORAGE_REFERENCE.compareAndSet(null, new ContextStorageBridge(storage));
    return getStorage();
  }

  private GrpcSingletons() {}
}
