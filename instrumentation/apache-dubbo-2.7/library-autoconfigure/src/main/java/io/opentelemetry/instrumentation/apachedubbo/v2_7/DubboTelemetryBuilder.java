/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.OpenTelemetryConfigurable;
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
  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  public DubboTelemetry build() {
    DubboRpcAttributesGetter rpcAttributesGetter = new DubboRpcAttributesGetter();
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) spanNameExtractor).internalInitialize(openTelemetry);
    SpanNameExtractor<DubboRequest> clientSpanNameExtractor =
        clientSpanNameExtractorCustomizer.apply(spanNameExtractor);
    SpanNameExtractor<DubboRequest> serverSpanNameExtractor =
        serverSpanNameExtractorCustomizer.apply(spanNameExtractor);
    DubboClientNetworkAttributesGetter netClientAttributesGetter =
        new DubboClientNetworkAttributesGetter(openTelemetry);
    DubboNetworkServerAttributesGetter netServerAttributesGetter =
        new DubboNetworkServerAttributesGetter();

    AttributesExtractor<DubboRequest, Result> serverRpcAttributesExtractor =
        RpcServerAttributesExtractor.create(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) serverRpcAttributesExtractor).internalInitialize(openTelemetry);
    AttributesExtractor<DubboRequest, Result> clientRpcAttributesExtractor =
        RpcClientAttributesExtractor.create(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) clientRpcAttributesExtractor).internalInitialize(openTelemetry);
    OperationMetrics serverRpcMetrics = RpcServerMetrics.get();
    ((OpenTelemetryConfigurable) serverRpcMetrics).internalInitialize(openTelemetry);
    OperationMetrics clientRpcMetrics = RpcClientMetrics.get();
    ((OpenTelemetryConfigurable) clientRpcMetrics).internalInitialize(openTelemetry);
    ContextCustomizer<DubboRequest> serverContextCustomizer =
        RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) serverContextCustomizer).internalInitialize(openTelemetry);
    ContextCustomizer<DubboRequest> clientContextCustomizer =
        RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter);
    ((OpenTelemetryConfigurable) clientContextCustomizer).internalInitialize(openTelemetry);

    InstrumenterBuilder<DubboRequest, Result> serverInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor)
            .addAttributesExtractor(serverRpcAttributesExtractor)
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(serverRpcMetrics)
            .addContextCustomizer(serverContextCustomizer);
    setRpcServerExceptionEventExtractor(serverInstrumenterBuilder);

    InstrumenterBuilder<DubboRequest, Result> clientInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor)
            .addAttributesExtractor(clientRpcAttributesExtractor)
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractors(attributesExtractors)
            .addOperationMetrics(clientRpcMetrics)
            .addContextCustomizer(clientContextCustomizer);
    setRpcClientExceptionEventExtractor(clientInstrumenterBuilder);

    return new DubboTelemetry(
        serverInstrumenterBuilder.buildServerInstrumenter(new DubboHeadersGetter()),
        clientInstrumenterBuilder.buildClientInstrumenter(new DubboHeadersSetter()));
  }
}
