/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcExceptionEventExtractors;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTelemetry}. */
public final class DubboTelemetryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorCustomizer =
      UnaryOperator.identity();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorCustomizer =
      UnaryOperator.identity();

  DubboTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
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
   * Sets a customizer that receives the default client {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setClientSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorCustomizer) {
    this.clientSpanNameExtractorCustomizer = clientSpanNameExtractorCustomizer;
    return this;
  }

  /**
   * Sets a customizer that receives the default server {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setServerSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorCustomizer) {
    this.serverSpanNameExtractorCustomizer = serverSpanNameExtractorCustomizer;
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
        clientSpanNameExtractorCustomizer.apply(spanNameExtractor);
    SpanNameExtractor<DubboRequest> serverSpanNameExtractor =
        serverSpanNameExtractorCustomizer.apply(spanNameExtractor);
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

    Experimental.setExceptionEventExtractor(
        serverInstrumenterBuilder, RpcExceptionEventExtractors.server());
    Experimental.setExceptionEventExtractor(
        clientInstrumenterBuilder, RpcExceptionEventExtractors.client());
    return new DubboTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(DubboHeadersGetter.INSTANCE),
        clientInstrumenterBuilder.buildClientInstrumenter(DubboHeadersSetter.INSTANCE));
  }
}
