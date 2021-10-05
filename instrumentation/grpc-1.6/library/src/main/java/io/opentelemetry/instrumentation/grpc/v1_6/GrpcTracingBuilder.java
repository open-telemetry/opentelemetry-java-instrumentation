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
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetClientAttributesExtractor;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link GrpcTracing}. */
public final class GrpcTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grpc-1.6";

  private final OpenTelemetry openTelemetry;

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
    InstrumenterBuilder<GrpcRequest, Status> instrumenterBuilder =
        Instrumenter.newBuilder(openTelemetry, INSTRUMENTATION_NAME, new GrpcSpanNameExtractor());
    instrumenterBuilder
        .setSpanStatusExtractor(new GrpcSpanStatusExtractor())
        .addAttributesExtractors(new GrpcRpcAttributesExtractor(), new GrpcAttributesExtractor())
        .addAttributesExtractors(additionalExtractors);
    return new GrpcTracing(
        instrumenterBuilder
            .addAttributesExtractor(new GrpcNetServerAttributesExtractor())
            .newServerInstrumenter(GrpcExtractAdapter.GETTER),
        // gRPC client interceptors require two phases, one to set up request and one to execute.
        // So we go ahead and inject manually in this instrumentation.
        instrumenterBuilder
            .addAttributesExtractor(new GrpcNetClientAttributesExtractor())
            .newInstrumenter(SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators(),
        captureExperimentalSpanAttributes);
  }
}
