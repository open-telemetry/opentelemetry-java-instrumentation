/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import java.util.ArrayList;
import java.util.List;
import org.apache.dubbo.rpc.Result;

public final class TracingFilterBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();

  public TracingFilterBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  public TracingFilter build() {
    DubboRpcAttributesExtractor rpcAttributesExtractor = new DubboRpcAttributesExtractor();
    DubboNetAttributesExtractor netAttributesExtractor = new DubboNetAttributesExtractor();
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesExtractor);

    InstrumenterBuilder<DubboRequest, Result> builder =
        Instrumenter.<DubboRequest, Result>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(rpcAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor);

    for (AttributesExtractor<DubboRequest, Result> attributesExtractor : attributesExtractors) {
      builder.addAttributesExtractor(attributesExtractor);
    }

    return new TracingFilter(
        builder.newServerInstrumenter(new DubboHeadersGetter()),
        builder.newClientInstrumenter(new DubboHeadersSetter()));
  }
}
