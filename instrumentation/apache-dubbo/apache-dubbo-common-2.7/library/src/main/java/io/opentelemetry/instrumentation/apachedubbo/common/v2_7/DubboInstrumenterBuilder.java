/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.common.v2_7;

import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcClientExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcExceptionEventExtractors.setRpcServerExceptionEventExtractor;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.internal.DubboClientNetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.internal.RpcSchemaUrls;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.apache.dubbo.rpc.Result;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DubboInstrumenterBuilder {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<DubboRequest, Result>> clientAttributesExtractors =
      new ArrayList<>();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorCustomizer =
      UnaryOperator.identity();
  private UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorCustomizer =
      UnaryOperator.identity();

  public DubboInstrumenterBuilder(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = requireNonNull(openTelemetry, "openTelemetry");
    this.instrumentationName = instrumentationName;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  @CanIgnoreReturnValue
  public DubboInstrumenterBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(requireNonNull(attributesExtractor, "attributesExtractor"));
    return this;
  }

  /**
   * Adds an additional client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items.
   */
  @CanIgnoreReturnValue
  public DubboInstrumenterBuilder addClientAttributeExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    clientAttributesExtractors.add(requireNonNull(attributesExtractor, "attributesExtractor"));
    return this;
  }

  /**
   * Sets a customizer that receives the default client {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboInstrumenterBuilder setClientSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> clientSpanNameExtractorCustomizer) {
    this.clientSpanNameExtractorCustomizer =
        requireNonNull(clientSpanNameExtractorCustomizer, "clientSpanNameExtractorCustomizer");
    return this;
  }

  /**
   * Sets a customizer that receives the default server {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public DubboInstrumenterBuilder setServerSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> serverSpanNameExtractorCustomizer) {
    this.serverSpanNameExtractorCustomizer =
        requireNonNull(serverSpanNameExtractorCustomizer, "serverSpanNameExtractorCustomizer");
    return this;
  }

  /**
   * Returns a new {@link DubboInstrumenters} with the settings of this {@link
   * DubboInstrumenterBuilder}.
   */
  public DubboInstrumenters build() {
    DubboRpcAttributesGetter clientRpcAttributesGetter = new DubboRpcAttributesGetter(true);
    DubboRpcAttributesGetter serverRpcAttributesGetter = new DubboRpcAttributesGetter(false);
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(clientRpcAttributesGetter);
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
                openTelemetry, instrumentationName, serverSpanNameExtractor)
            .setSchemaUrl(RpcSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(RpcServerAttributesExtractor.create(serverRpcAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
            .addAttributesExtractor(netServerAttributesGetter)
            .addAttributesExtractors(attributesExtractors)
            .setSpanStatusExtractor(new DubboRpcSpanStatusExtractor(false))
            .addOperationMetrics(RpcServerMetrics.get());
    setRpcServerExceptionEventExtractor(serverInstrumenterBuilder);

    InstrumenterBuilder<DubboRequest, Result> clientInstrumenterBuilder =
        Instrumenter.<DubboRequest, Result>builder(
                openTelemetry, instrumentationName, clientSpanNameExtractor)
            .setSchemaUrl(RpcSchemaUrls.SCHEMA_URL)
            .addAttributesExtractor(RpcClientAttributesExtractor.create(clientRpcAttributesGetter))
            .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
            .addAttributesExtractors(attributesExtractors)
            .addAttributesExtractors(clientAttributesExtractors)
            .setSpanStatusExtractor(new DubboRpcSpanStatusExtractor(true))
            .addOperationMetrics(RpcClientMetrics.get());
    setRpcClientExceptionEventExtractor(clientInstrumenterBuilder);

    return new DubboInstrumenters(
        serverInstrumenterBuilder.buildServerInstrumenter(new DubboHeadersGetter()),
        clientInstrumenterBuilder.buildClientInstrumenter(new DubboHeadersSetter()));
  }
}
