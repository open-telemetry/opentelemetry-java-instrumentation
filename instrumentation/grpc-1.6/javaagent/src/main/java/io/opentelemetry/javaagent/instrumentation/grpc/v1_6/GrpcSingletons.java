/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import static java.util.Collections.emptyList;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

// Holds singleton references.
public final class GrpcSingletons {

  public static final VirtualField<ManagedChannelBuilder<?>, Boolean>
      MANAGED_CHANNEL_BUILDER_INSTRUMENTED =
          VirtualField.find(ManagedChannelBuilder.class, Boolean.class);

  public static final VirtualField<ServerBuilder<?>, Boolean> SERVER_BUILDER_INSTRUMENTED =
      VirtualField.find(ServerBuilder.class, Boolean.class);

  public static final ClientInterceptor CLIENT_INTERCEPTOR;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  private static final AtomicReference<Context.Storage> STORAGE_REFERENCE = new AtomicReference<>();

  static {
    boolean emitMessageEvents =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", "grpc", "emit_message_events")
            .orElse(true);

    boolean experimentalSpanAttributes =
        DeclarativeConfigUtil.getBoolean(
                GlobalOpenTelemetry.get(), "java", "grpc", "experimental_span_attributes")
            .orElse(false);

    List<String> clientRequestMetadata =
        DeclarativeConfigUtil.getList(
                GlobalOpenTelemetry.get(), "java", "grpc", "capture_metadata", "client", "request")
            .orElse(emptyList());
    List<String> serverRequestMetadata =
        DeclarativeConfigUtil.getList(
                GlobalOpenTelemetry.get(), "java", "grpc", "capture_metadata", "server", "request")
            .orElse(emptyList());

    GrpcTelemetry telemetry =
        GrpcTelemetry.builder(GlobalOpenTelemetry.get())
            .setEmitMessageEvents(emitMessageEvents)
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
