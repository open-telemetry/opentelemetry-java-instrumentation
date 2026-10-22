/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.ServerStreamTracer;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

// Holds singleton references.
public class GrpcSingletons {

  private static final String AGENT_STREAM_TRACER_FACTORY =
      "io.opentelemetry.instrumentation.grpc.v1_6.TracingServerStreamTracerFactory";
  private static final String LIBRARY_STREAM_TRACER_FACTORY =
      "application.io.opentelemetry.instrumentation.grpc.v1_6.TracingServerStreamTracerFactory";

  public static final VirtualField<ManagedChannelBuilder<?>, Boolean>
      MANAGED_CHANNEL_BUILDER_INSTRUMENTED =
          VirtualField.find(ManagedChannelBuilder.class, Boolean.class);

  public static final VirtualField<ServerBuilder<?>, Boolean> SERVER_BUILDER_INSTRUMENTED =
      VirtualField.find(ServerBuilder.class, Boolean.class);

  private static final ClientInterceptor clientInterceptor;

  private static final GrpcTelemetry grpcTelemetry;

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
    grpcTelemetry = telemetryBuilder.build();

    clientInterceptor = grpcTelemetry.createClientInterceptor();
  }

  public static ClientInterceptor clientInterceptor() {
    return clientInterceptor;
  }

  @Nullable
  public static Context.Storage storage() {
    return storageReference.get();
  }

  public static Context.Storage setStorage(Context.Storage storage) {
    storageReference.compareAndSet(null, new ContextStorageBridge(storage));
    return storage();
  }

  public static void configureServerBuilder(ServerBuilder<?> serverBuilder) {
    grpcTelemetry.configureServerBuilder(serverBuilder);
  }

  public static List<? extends ServerStreamTracer.Factory> deduplicateServerStreamTracerFactories(
      List<? extends ServerStreamTracer.Factory> factories) {
    boolean hasAgentFactory = false;
    boolean hasLibraryFactory = false;
    for (ServerStreamTracer.Factory factory : factories) {
      String className = factory.getClass().getName();
      if (className.equals(AGENT_STREAM_TRACER_FACTORY)) {
        hasAgentFactory = true;
      } else if (className.equals(LIBRARY_STREAM_TRACER_FACTORY)) {
        hasLibraryFactory = true;
      }
    }
    if (!hasAgentFactory || !hasLibraryFactory) {
      return factories;
    }

    // The agent's outer server interceptor also owns telemetry for registered methods.
    List<ServerStreamTracer.Factory> filtered = new ArrayList<>(factories.size());
    for (ServerStreamTracer.Factory factory : factories) {
      if (!factory.getClass().getName().equals(LIBRARY_STREAM_TRACER_FACTORY)) {
        filtered.add(factory);
      }
    }
    return Collections.unmodifiableList(filtered);
  }

  private GrpcSingletons() {}
}
