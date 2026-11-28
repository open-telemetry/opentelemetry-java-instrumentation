/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v3_1;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboInstrumenterBuilder;
import io.opentelemetry.instrumentation.apachedubbo.common.v2_7.DubboRequest;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.UnaryOperator;
import org.apache.dubbo.rpc.Result;

/** A builder of {@link DubboTelemetry}. */
public final class DubboTelemetryBuilder {

  private final DubboInstrumenterBuilder builder;

  DubboTelemetryBuilder(OpenTelemetry openTelemetry) {
    builder = new DubboInstrumenterBuilder(openTelemetry, "io.opentelemetry.apache-dubbo-3.1");
  }

  /** Adds an attributes extractor to both client and server instrumentation. */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    builder.addAttributesExtractor(attributesExtractor);
    return this;
  }

  /** Adds an attributes extractor to client instrumentation only. */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<DubboRequest, Result> attributesExtractor) {
    builder.addClientAttributeExtractor(attributesExtractor);
    return this;
  }

  /** Sets a customizer for the default client span name extractor. */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setClientSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> customizer) {
    builder.setClientSpanNameExtractorCustomizer(customizer);
    return this;
  }

  /** Sets a customizer for the default server span name extractor. */
  @CanIgnoreReturnValue
  public DubboTelemetryBuilder setServerSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<DubboRequest>> customizer) {
    builder.setServerSpanNameExtractorCustomizer(customizer);
    return this;
  }

  /** Builds a new {@link DubboTelemetry} with the configured settings. */
  public DubboTelemetry build() {
    return new DubboTelemetry(builder.build());
  }
}
