/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.core.response.SofaResponse;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.sofa.rpc.internal.SofaRpcClientNetworkAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

/** A builder of {@link SofaRpcTelemetry}. */
public final class SofaRpcTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.sofa-rpc";

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<SofaRpcRequest, SofaResponse>> attributesExtractors =
      new ArrayList<>();
  private UnaryOperator<SpanNameExtractor<SofaRpcRequest>> clientSpanNameExtractorTransformer =
      UnaryOperator.identity();
  private UnaryOperator<SpanNameExtractor<SofaRpcRequest>> serverSpanNameExtractorTransformer =
      UnaryOperator.identity();

  SofaRpcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets the {@code peer.service} attribute for RPC client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<SofaRpcRequest, SofaResponse> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /** Sets custom client {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder setClientSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<SofaRpcRequest>> clientSpanNameExtractor) {
    this.clientSpanNameExtractorTransformer = clientSpanNameExtractor;
    return this;
  }

  /** Sets custom server {@link SpanNameExtractor} via transform function. */
  @CanIgnoreReturnValue
  public SofaRpcTelemetryBuilder setServerSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<SofaRpcRequest>> serverSpanNameExtractor) {
    this.serverSpanNameExtractorTransformer = serverSpanNameExtractor;
    return this;
  }

  /**
   * Returns a new {@link SofaRpcTelemetry} with the settings of this {@link
   * SofaRpcTelemetryBuilder}.
   */
  public SofaRpcTelemetry build() {
    SofaRpcAttributesGetter rpcAttributesGetter = SofaRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<SofaRpcRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    SpanNameExtractor<SofaRpcRequest> clientSpanNameExtractor =
        clientSpanNameExtractorTransformer.apply(spanNameExtractor);
    SpanNameExtractor<SofaRpcRequest> serverSpanNameExtractor =
        serverSpanNameExtractorTransformer.apply(spanNameExtractor);
    SofaRpcClientNetworkAttributesGetter netClientAttributesGetter =
        new SofaRpcClientNetworkAttributesGetter();
    SofaRpcNetworkServerAttributesGetter netServerAttributesGetter =
        new SofaRpcNetworkServerAttributesGetter();

    InstrumenterBuilder<SofaRpcRequest, SofaResponse> serverInstrumenterBuilder =
        Instrumenter.<SofaRpcRequest, SofaResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractor(new SofaRpcErrorAttributesExtractor())
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(RpcServerMetrics.get());

    InstrumenterBuilder<SofaRpcRequest, SofaResponse> clientInstrumenterBuilder =
        Instrumenter.<SofaRpcRequest, SofaResponse>builder(
                openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(new SofaRpcErrorAttributesExtractor())
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(RpcClientMetrics.get());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(PEER_SERVICE, peerService));
    }

    return new SofaRpcTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(SofaRpcHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(SofaRpcHeadersSetter.INSTANCE));
  }
}
