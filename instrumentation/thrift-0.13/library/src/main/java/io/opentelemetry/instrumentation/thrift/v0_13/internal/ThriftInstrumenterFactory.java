/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcMetricsContextCustomizers;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcServerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSizeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.rpc.RpcSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.internal.Experimental;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.thrift-0.13";

  private static final ThriftRpcAttributesGetter rpcAttributesGetter =
      new ThriftRpcAttributesGetter();
  private static final SpanStatusExtractor<ThriftRequest, ThriftResponse> spanStatusExtractor =
      (spanStatusBuilder, request, response, error) -> {
        if (response != null && response.isFailed()) {
          spanStatusBuilder.setStatus(StatusCode.ERROR);
        } else {
          SpanStatusExtractor.getDefault().extract(spanStatusBuilder, request, response, error);
        }
      };

  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  public static Instrumenter<ThriftRequest, ThriftResponse> createClientInstrumenter(
      OpenTelemetry openTelemetry,
      UnaryOperator<SpanNameExtractor<ThriftRequest>> clientSpanNameExtractorCustomizer,
      List<AttributesExtractor<ThriftRequest, ThriftResponse>> additionalExtractors,
      List<AttributesExtractor<ThriftRequest, ThriftResponse>> additionalClientExtractors) {

    SpanNameExtractor<ThriftRequest> originalSpanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    SpanNameExtractor<ThriftRequest> clientSpanNameExtractor =
        clientSpanNameExtractorCustomizer.apply(originalSpanNameExtractor);

    InstrumenterBuilder<ThriftRequest, ThriftResponse> clientInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, clientSpanNameExtractor);

    ThriftClientNetworkAttributesGetter netClientAttributesGetter =
        new ThriftClientNetworkAttributesGetter();

    clientInstrumenterBuilder
        .setSpanStatusExtractor(spanStatusExtractor)
        .addAttributesExtractors(additionalExtractors)
        .addAttributesExtractor(RpcClientAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(ServerAttributesExtractor.create(netClientAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netClientAttributesGetter))
        .addAttributesExtractors(additionalClientExtractors)
        .addOperationMetrics(RpcClientMetrics.get())
        .addContextCustomizer(
            RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));

    return clientInstrumenterBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  @SuppressWarnings("deprecation") // RpcMetricsContextCustomizers is deprecated for removal in 3.0
  public static Instrumenter<ThriftRequest, ThriftResponse> createServerInstrumenter(
      OpenTelemetry openTelemetry,
      UnaryOperator<SpanNameExtractor<ThriftRequest>> serverSpanNameExtractorCustomizer,
      List<AttributesExtractor<ThriftRequest, ThriftResponse>> additionalExtractors,
      List<AttributesExtractor<ThriftRequest, ThriftResponse>> additionalServerExtractors) {
    SpanNameExtractor<ThriftRequest> originalSpanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesGetter);
    SpanNameExtractor<ThriftRequest> serverSpanNameExtractor =
        serverSpanNameExtractorCustomizer.apply(originalSpanNameExtractor);

    InstrumenterBuilder<ThriftRequest, ThriftResponse> serverInstrumenterBuilder =
        Instrumenter.builder(openTelemetry, INSTRUMENTATION_NAME, serverSpanNameExtractor);

    ThriftServerNetworkAttributesGetter netServerAttributesGetter =
        new ThriftServerNetworkAttributesGetter();

    serverInstrumenterBuilder
        .setSpanStatusExtractor(spanStatusExtractor)
        .addAttributesExtractors(additionalExtractors)
        .addAttributesExtractor(RpcServerAttributesExtractor.create(rpcAttributesGetter))
        .addAttributesExtractor(ServerAttributesExtractor.create(netServerAttributesGetter))
        .addAttributesExtractor(NetworkAttributesExtractor.create(netServerAttributesGetter))
        .addAttributesExtractors(additionalServerExtractors)
        .addOperationMetrics(RpcServerMetrics.get())
        .addContextCustomizer(
            RpcMetricsContextCustomizers.dualEmitContextCustomizer(rpcAttributesGetter));
    Experimental.addOperationListenerAttributesExtractor(
        serverInstrumenterBuilder, RpcSizeAttributesExtractor.create(rpcAttributesGetter));

    return serverInstrumenterBuilder.buildServerInstrumenter(new ThriftRequestGetter());
  }

  private ThriftInstrumenterFactory() {}
}
