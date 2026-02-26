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
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ContextStorageBridge;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Internal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;

// Holds singleton references.
public final class GrpcSingletons {

  public static final VirtualField<ManagedChannelBuilder<?>, Boolean>
      MANAGED_CHANNEL_BUILDER_INSTRUMENTED =
          VirtualField.find(ManagedChannelBuilder.class, Boolean.class);

  public static final VirtualField<ServerBuilder<?>, Boolean> SERVER_BUILDER_INSTRUMENTED =
      VirtualField.find(ServerBuilder.class, Boolean.class);

  private static final GrpcTelemetry TELEMETRY;

  public static final ServerInterceptor SERVER_INTERCEPTOR;

  private static final AtomicReference<Context.Storage> STORAGE_REFERENCE = new AtomicReference<>();

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "grpc");
    boolean emitMessageEvents = config.getBoolean("emit_message_events", true);

    boolean experimentalSpanAttributes =
        config.getBoolean("experimental_span_attributes/development", false);

    List<String> clientRequestMetadata =
        config
            .get("capture_metadata")
            .get("client")
            .getScalarList("request", String.class, emptyList());
    List<String> serverRequestMetadata =
        config
            .get("capture_metadata")
            .get("server")
            .getScalarList("request", String.class, emptyList());

    GrpcTelemetry telemetry =
        GrpcTelemetry.builder(GlobalOpenTelemetry.get())
            .setEmitMessageEvents(emitMessageEvents)
            .setCaptureExperimentalSpanAttributes(experimentalSpanAttributes)
            .setCapturedClientRequestMetadata(clientRequestMetadata)
            .setCapturedServerRequestMetadata(serverRequestMetadata)
            .build();

    TELEMETRY = telemetry;
    SERVER_INTERCEPTOR = telemetry.createServerInterceptor();
  }

  public static ClientInterceptor createClientInterceptor(@Nullable String target) {
    return Internal.createClientInterceptor(TELEMETRY, target);
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
