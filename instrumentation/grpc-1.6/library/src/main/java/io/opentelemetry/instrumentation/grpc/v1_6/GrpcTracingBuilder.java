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
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetClientAttributesAdapter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** A builder of {@link GrpcTracing}. */
public final class GrpcTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grpc-1.6";

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;

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

  /** Sets the {@code peer.service} attribute for http client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
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
    InstrumenterBuilder<GrpcRequest, Status> clientInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, new GrpcSpanNameExtractor());
    InstrumenterBuilder<GrpcRequest, Status> serverInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, new GrpcSpanNameExtractor());

    Stream.of(clientInstrumenterBuilder, serverInstrumenterBuilder)
        .forEach(
            instrumenter ->
                instrumenter
                    .setSpanStatusExtractor(new GrpcSpanStatusExtractor())
                    .addAttributesExtractors(
                        new GrpcRpcAttributesExtractor(), new GrpcAttributesExtractor())
                    .addAttributesExtractors(additionalExtractors));

    GrpcNetClientAttributesAdapter netClientAttributesExtractor =
        new GrpcNetClientAttributesAdapter();

    clientInstrumenterBuilder.addAttributesExtractor(new NetClientAttributesExtractor<>(netClientAttributesExtractor));
    serverInstrumenterBuilder.addAttributesExtractor(new GrpcNetServerAttributesExtractor());

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
