/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcClientNetworkAttributesGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/** A builder of {@link GrpcTelemetry}. */
public final class GrpcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grpc-1.6";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;

  @Nullable
  private Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
      clientSpanNameExtractorTransformer;

  @Nullable
  private Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
      serverSpanNameExtractorTransformer;

  private final List<AttributesExtractor<? super GrpcRequest, ? super Status>>
      additionalExtractors = new ArrayList<>();
  private final List<AttributesExtractor<? super GrpcRequest, ? super Status>>
      additionalClientExtractors = new ArrayList<>();
  private final List<AttributesExtractor<? super GrpcRequest, ? super Status>>
      additionalServerExtractors = new ArrayList<>();

  private boolean captureExperimentalSpanAttributes;
  private List<String> capturedClientRequestMetadata = Collections.emptyList();
  private List<String> capturedServerRequestMetadata = Collections.emptyList();

  GrpcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder addAttributeExtractor(
      AttributesExtractor<? super GrpcRequest, ? super Status> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<? super GrpcRequest, ? super Status> attributesExtractor) {
    additionalClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra server-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder addServerAttributeExtractor(
      AttributesExtractor<? super GrpcRequest, ? super Status> attributesExtractor) {
    additionalServerExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets custom client {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setClientSpanNameExtractor(
      Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
          clientSpanNameExtractor) {
    this.clientSpanNameExtractorTransformer = clientSpanNameExtractor;
    return this;
  }

  /** Sets custom server {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setServerSpanNameExtractor(
      Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
          serverSpanNameExtractor) {
    this.serverSpanNameExtractorTransformer = serverSpanNameExtractor;
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setPeerService(String peerService) {
    this.peerService = peerService;
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /** Sets which metadata request values should be captured as span attributes on client spans. */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setCapturedClientRequestMetadata(
      List<String> capturedClientRequestMetadata) {
    this.capturedClientRequestMetadata = capturedClientRequestMetadata;
    return this;
  }

  /** Sets which metadata request values should be captured as span attributes on server spans. */
  @CanIgnoreReturnValue
  public GrpcTelemetryBuilder setCapturedServerRequestMetadata(
      List<String> capturedServerRequestMetadata) {
    this.capturedServerRequestMetadata = capturedServerRequestMetadata;
    return this;
  }

  /** Returns a new {@link GrpcTelemetry} with the settings of this {@link GrpcTelemetryBuilder}. */
  @SuppressWarnings("deprecation") // using createForServerSide() for the old->stable semconv story
  public GrpcTelemetry build() {
    SpanNameExtractor<GrpcRequest> originalSpanNameExtractor = new GrpcSpanNameExtractor();

    SpanNameExtractor<? super GrpcRequest> clientSpanNameExtractor = originalSpanNameExtractor;
    if (clientSpanNameExtractorTransformer != null) {
      clientSpanNameExtractor = clientSpanNameExtractorTransformer.apply(originalSpanNameExtractor);
    }

    SpanNameExtractor<? super GrpcRequest> serverSpanNameExtractor = originalSpanNameExtractor;
    if (serverSpanNameExtractorTransformer != null) {
      serverSpanNameExtractor = serverSpanNameExtractorTransformer.apply(originalSpanNameExtractor);
    }

    InstrumenterBuilder<GrpcRequest, Status> clientInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor);
    InstrumenterBuilder<GrpcRequest, Status> serverInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor);

    GrpcClientNetworkAttributesGetter netClientAttributesGetter =
        new GrpcClientNetworkAttributesGetter();
    GrpcNetworkServerAttributesGetter netServerAttributesGetter =
        new GrpcNetworkServerAttributesGetter();
    GrpcRpcAttributesGetter rpcAttributesGetter = GrpcRpcAttributesGetter.INSTANCE;

    clientInstrumenterBuilder
        .setSpanStatusExtractor(GrpcSpanStatusExtractor.CLIENT)
        .addAttributesExtractors(additionalExtractors)
        .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
        .addAttributesExtractors(additionalClientExtractors)
        .addAttributesExtractor(
            new GrpcAttributesExtractor(
                GrpcRpcAttributesGetter.INSTANCE, capturedClientRequestMetadata))
        .addOperationMetrics(RpcClientMetrics.get());
    serverInstrumenterBuilder
        .setSpanStatusExtractor(GrpcSpanStatusExtractor.SERVER)
        .addAttributesExtractors(additionalExtractors)
        .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(
            ServerAttributesExtractor.createForServerSide(netServerAttributesGetter))
        .addAttributesExtractor(ClientAttributesExtractor.create(netServerAttributesGetter))
        .addAttributesExtractor(
            new GrpcAttributesExtractor(
                GrpcRpcAttributesGetter.INSTANCE, capturedServerRequestMetadata))
        .addAttributesExtractors(additionalServerExtractors)
        .addOperationMetrics(RpcServerMetrics.get());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    }

    return new GrpcTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(GrpcRequestGetter.INSTANCE),
        // gRPC client interceptors require two phases, one to set up request and one to execute.
        // So we go ahead and inject manually in this instrumentation.
        clientInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators(),
        captureExperimentalSpanAttributes);
  }
}
