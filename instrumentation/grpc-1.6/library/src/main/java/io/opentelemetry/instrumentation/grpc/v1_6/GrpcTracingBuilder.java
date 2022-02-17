/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetClientAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A builder of {@link GrpcTracing}. */
public final class GrpcTracingBuilder {

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

  private boolean captureExperimentalSpanAttributes;

  GrpcTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  public GrpcTracingBuilder addAttributeExtractor(
      AttributesExtractor<? super GrpcRequest, ? super Status> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets custom client {@link SpanNameExtractor} via transform function. */
  public GrpcTracingBuilder setClientSpanNameExtractor(
      Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
          clientSpanNameExtractor) {
    this.clientSpanNameExtractorTransformer = clientSpanNameExtractor;
    return this;
  }

  /** Sets custom server {@link SpanNameExtractor} via transform function. */
  public GrpcTracingBuilder setServerSpanNameExtractor(
      Function<SpanNameExtractor<GrpcRequest>, ? extends SpanNameExtractor<? super GrpcRequest>>
          serverSpanNameExtractor) {
    this.serverSpanNameExtractorTransformer = serverSpanNameExtractor;
    return this;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public GrpcTracingBuilder setPeerService(String peerService) {
    this.peerService = peerService;
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  public GrpcTracingBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /** Returns a new {@link GrpcTracing} with the settings of this {@link GrpcTracingBuilder}. */
  public GrpcTracing build() {
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

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(
            instrumenter ->
                instrumenter
                    .setSpanStatusExtractor(new GrpcSpanStatusExtractor())
                    .addAttributesExtractors(
                        new GrpcRpcAttributesExtractor(), new GrpcAttributesExtractor())
                    .addAttributesExtractors(additionalExtractors));

    GrpcNetClientAttributesGetter netClientAttributesExtractor =
        new GrpcNetClientAttributesGetter();

    clientInstrumenterBuilder.addAttributesExtractor(
        NetClientAttributesExtractor.create(netClientAttributesExtractor));
    serverInstrumenterBuilder.addAttributesExtractor(
        NetServerAttributesExtractor.create(new GrpcNetServerAttributesGetter()));

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(SemanticAttributes.PEER_SERVICE, peerService));
    } else {
      clientInstrumenterBuilder.addAttributesExtractor(
          PeerServiceAttributesExtractor.create(netClientAttributesExtractor));
    }

    return new GrpcTracing(
        serverInstrumenterBuilder.newServerInstrumenter(GrpcRequestGetter.INSTANCE),
        // gRPC client interceptors require two phases, one to set up request and one to execute.
        // So we go ahead and inject manually in this instrumentation.
        clientInstrumenterBuilder.newInstrumenter(SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators(),
        captureExperimentalSpanAttributes);
  }
}
