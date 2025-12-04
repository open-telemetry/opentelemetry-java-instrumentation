/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTelemetry}. */
public final class DubboTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  // copied from PeerIncubatingAttributes
  private static final AttributeKey<String> PEER_SERVICE = AttributeKey.stringKey("peer.service");

  private final OpenTelemetry openTelemetry;
  @Nullable private String peerService;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorTransformer =
      UnaryOperator.identity();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorTransformer =
      UnaryOperator.identity();

  DubboTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets the {@code peer.service} attribute for http client spans. */
  public void setPeerService(String peerService) {
    this.peerService = peerService;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Sets custom client {@link SpanNameExtractor} via transform function.
   *
   * @deprecated Use {@link #setClientSpanNameExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setClientSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractor) {
    return setClientSpanNameExtractorCustomizer(clientSpanNameExtractor);
  }

  /**
   * Sets a customizer that receives the default client {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setClientSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorCustomizer) {
    this.clientSpanNameExtractorTransformer = clientSpanNameExtractorCustomizer;
    return this;
  }

  /**
   * Sets custom server {@link SpanNameExtractor} via transform function.
   *
   * @deprecated Use {@link #setServerSpanNameExtractorCustomizer(UnaryOperator)} instead.
   */
  @Deprecated
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setServerSpanNameExtractor(
      UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractor) {
    return setServerSpanNameExtractorCustomizer(serverSpanNameExtractor);
  }

  /**
   * Sets a customizer that receives the default server {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setServerSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorCustomizer) {
    this.serverSpanNameExtractorTransformer = serverSpanNameExtractorCustomizer;
    return this;
  }

  /**
   * Returns a new {@link DubboTelemetry} with the settings of this {@link DubboTelemetryBuilder}.
   */
  public DubboTelemetry build() {
    DubboRpcAttributesGetter rpcAttributesGetter = DubboRpcAttributesGetter.INSTANCE;
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    SpanNameExtractor<DubboRequest> clientSpanNameExtractor =
        clientSpanNameExtractorTransformer.apply(spanNameExtractor);
    SpanNameExtractor<DubboRequest> serverSpanNameExtractor =
        serverSpanNameExtractorTransformer.apply(spanNameExtractor);
    DubboClientNetworkAttributesGetter netClientAttributesGetter =
        new DubboClientNetworkAttributesGetter();
    DubboNetworkServerAttributesGetter netServerAttributesGetter =
        new DubboNetworkServerAttributesGetter();

    InstrumenterBuilder<DubboRequest, Result> serverInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(RpcServerMetrics.get());

    InstrumenterBuilder<DubboRequest, Result> clientInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(RpcClientMetrics.get());

    if (peerService != null) {
      clientInstrumenterBuilder.addAttributesExtractor(
          AttributesExtractor.constant(PEER_SERVICE, peerService));
    }

    return new DubboTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(DubboHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(DubboHeadersSetter.INSTANCE));
  }
}
