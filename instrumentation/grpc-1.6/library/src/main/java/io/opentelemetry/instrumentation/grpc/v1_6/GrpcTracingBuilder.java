/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.TracingBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcNetAttributesExtractor;

/** A builder of {@link GrpcTracing}. */
public final class GrpcTracingBuilder
    extends TracingBuilder<GrpcRequest, Status, GrpcTracingBuilder> {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.grpc-1.6";

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;

  GrpcTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
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
        .addAttributesExtractors(
            new GrpcNetAttributesExtractor(),
            new GrpcRpcAttributesExtractor(),
            new GrpcAttributesExtractor());
    return new GrpcTracing(
        instrumenterBuilder.newServerInstrumenter(this, GrpcExtractAdapter.GETTER),
        // gRPC client interceptors require two phases, one to set up request and one to execute.
        // So we go ahead and inject manually in this instrumentation.
        instrumenterBuilder.newInstrumenter(this, SpanKindExtractor.alwaysClient()),
        openTelemetry.getPropagators(),
        captureExperimentalSpanAttributes);
  }
}
