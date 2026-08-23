/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetryBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcConfig;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Internal;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

// Holds singleton references.
public class GrpcSingletons {

  public static final VirtualField<ManagedChannelBuilder<?>, Boolean>
      MANAGED_CHANNEL_BUILDER_INSTRUMENTED =
          VirtualField.find(ManagedChannelBuilder.class, Boolean.class);

  public static final VirtualField<ServerBuilder<?>, Boolean> SERVER_BUILDER_INSTRUMENTED =
      VirtualField.find(ServerBuilder.class, Boolean.class);

  private static final GrpcTelemetry telemetry;

  private static final ClientInterceptor clientInterceptor;

  private static final ServerInterceptor serverInterceptor;

  private static final AtomicReference<Context.Storage> storageReference = new AtomicReference<>();

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "grpc");
    GrpcConfig grpcConfig = GrpcConfig.create(openTelemetry);
    boolean emitMessageEvents = config.getBoolean("emit_message_events", true);

    boolean experimentalSpanAttributes =
        config.getBoolean("experimental_span_attributes/development", false);

    GrpcTelemetryBuilder telemetryBuilder =
        GrpcTelemetry.builder(openTelemetry)
            .setEmitMessageEvents(emitMessageEvents)
            .setCaptureExperimentalSpanAttributes(experimentalSpanAttributes);
    IncludeExclude clientRequestMetadata = grpcConfig.getClientRequestMetadata();
    if (clientRequestMetadata != null) {
      telemetryBuilder.setClientRequestMetadata(clientRequestMetadata);
    }
    IncludeExclude serverRequestMetadata = grpcConfig.getServerRequestMetadata();
    if (serverRequestMetadata != null) {
      telemetryBuilder.setServerRequestMetadata(serverRequestMetadata);
    }
    GrpcTelemetry configuredTelemetry = telemetryBuilder.build();

    telemetry = configuredTelemetry;
    clientInterceptor = Internal.createClientInterceptor(configuredTelemetry, null);
    serverInterceptor = configuredTelemetry.createServerInterceptor();
  }

  public static ClientInterceptor clientInterceptor() {
    return clientInterceptor;
  }

  public static ServerInterceptor serverInterceptor() {
    return serverInterceptor;
  }

  @Nullable
  public static Context.Storage storage() {
    return storageReference.get();
  }

  public static ClientInterceptor createClientInterceptor(@Nullable String target) {
    return Internal.createClientInterceptor(telemetry, target);
  }

  public static Context.Storage setStorage(Context.Storage storage) {
    storageReference.compareAndSet(null, new ContextStorageBridge(storage));
    return storage();
  }

  private GrpcSingletons() {}
}
