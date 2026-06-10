/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofarpc.v5_4;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.sofarpc.v5_4.internal.SofaRpcClientNetworkAttributesGetter;
import java.util.ArrayList;
import java.util.List;

/** A builder of {@link SofaRpcTelemetry}. */
public final class SofaRpcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.sofa-rpc-5.4";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<SofaRpcRequest, SofaResponse>> additionalExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<SofaRpcRequest, SofaResponse>> additionalClientExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<SofaRpcRequest, SofaResponse>> additionalServerExtractors =
      new ArrayList<>();

  SofaRpcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items for both client and server spans. The {@link AttributesExtractor} will be executed after
   * all default extractors.
   */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<SofaRpcRequest, SofaResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<SofaRpcRequest, SofaResponse> attributesExtractor) {
    additionalClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra server-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder addServerAttributeExtractor(
      AttributesExtractor<SofaRpcRequest, SofaResponse> attributesExtractor) {
    additionalServerExtractors.add(attributesExtractor);
    return this;
  }

  /** Returns a new {@link SofaRpcTelemetry} with the settings of this builder. */
  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  public SofaRpcTelemetry build() {
    SofaRpcAttributesGetter rpcAttributesGetter = new SofaRpcAttributesGetter();
    SpanNameExtractor<SofaRpcRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    SofaRpcClientNetworkAttributesGetter netClientAttributesGetter =
        new SofaRpcClientNetworkAttributesGetter();
    SofaRpcNetworkServerAttributesGetter netServerAttributesGetter =
        new SofaRpcNetworkServerAttributesGetter();

    InstrumenterBuilder<SofaRpcRequest, SofaResponse> serverInstrumenterBuilder =
        Instrumenter.<SofaRpcRequest, SofaResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addAttributesExtractors(additionalServerExtractors)
            .addOperationMetrics(RpcServerMetrics.get())
            .addContextCustomizer(
                RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));

    InstrumenterBuilder<SofaRpcRequest, SofaResponse> clientInstrumenterBuilder =
        Instrumenter.<SofaRpcRequest, SofaResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractors(additionalExtractors)
            .addAttributesExtractors(additionalClientExtractors)
            .addOperationMetrics(RpcClientMetrics.get())
            .addContextCustomizer(
                RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));

    return new SofaRpcTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(SofaRpcHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(SofaRpcHeadersSetter.INSTANCE));
  }
}
