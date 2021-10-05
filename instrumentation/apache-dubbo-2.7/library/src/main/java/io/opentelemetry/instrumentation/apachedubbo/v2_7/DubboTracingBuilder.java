/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNetClientAttributesExtractor;
import io.opentelemetry.instrumentation.apachedubbo.v2_7.internal.DubboNetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.rpc.RpcSpanNameExtractor;
import java.util.ArrayList;
import java.util.List;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTracing}. */
public final class DubboTracingBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dubbo-2.7";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<DubboRequest, Result>> attributesExtractors =
      new ArrayList<>();

  DubboTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items.
   */
  public DubboTracingBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    attributesExtractors.add(attributesExtractor);
    return this;
  }

  /** Returns a new {@link DubboTracing} with the settings of this {@link DubboTracingBuilder}. */
  public DubboTracing build() {
    DubboRpcAttributesExtractor rpcAttributesExtractor = new DubboRpcAttributesExtractor();
    SpanNameExtractor<DubboRequest> spanNameExtractor =
        RpcSpanNameExtractor.create(rpcAttributesExtractor);

    InstrumenterBuilder<DubboRequest, Result> builder =
        Instrumenter.<DubboRequest, Result>newBuilder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(rpcAttributesExtractor)
            .addAttributesExtractors(attributesExtractors);

    return new DubboTracing(
        builder
            .addAttributesExtractor(new DubboNetServerAttributesExtractor())
            .newServerInstrumenter(new DubboHeadersGetter()),
        builder
            .addAttributesExtractor(new DubboNetClientAttributesExtractor())
            .newClientInstrumenter(new DubboHeadersSetter()));
  }
}
