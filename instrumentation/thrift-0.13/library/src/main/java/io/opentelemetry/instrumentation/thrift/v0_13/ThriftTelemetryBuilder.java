/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.thrift.v0_13.internal.ThriftInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/** Builder for {@link ThriftTelemetry}. */
public final class ThriftTelemetryBuilder {
  private final OpenTelemetry openTelemetry;

  private UnaryOperator<SpanNameExtractor<ThriftRequest>> clientSpanNameExtractorCustomizer =
      UnaryOperator.identity();
  private UnaryOperator<SpanNameExtractor<ThriftRequest>> serverSpanNameExtractorCustomizer =
      UnaryOperator.identity();
  private final List<AttributesExtractor<ThriftRequest, ThriftResponse>> additionalExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<ThriftRequest, ThriftResponse>>
      additionalClientExtractors = new ArrayList<>();
  private final List<AttributesExtractor<ThriftRequest, ThriftResponse>>
      additionalServerExtractors = new ArrayList<>();

  ThriftTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Adds an additional {@link AttributesExtractor} to invoke to set attributes to instrumented
   * items. The {@link AttributesExtractor} will be executed after all default extractors.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder addAttributesExtractor(
      AttributesExtractor<ThriftRequest, ThriftResponse> attributesExtractor) {
    additionalExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra client-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder addClientAttributeExtractor(
      AttributesExtractor<ThriftRequest, ThriftResponse> attributesExtractor) {
    additionalClientExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Adds an extra server-only {@link AttributesExtractor} to invoke to set attributes to
   * instrumented items. The {@link AttributesExtractor} will be executed after all default
   * extractors.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder addServerAttributeExtractor(
      AttributesExtractor<ThriftRequest, ThriftResponse> attributesExtractor) {
    additionalServerExtractors.add(attributesExtractor);
    return this;
  }

  /**
   * Sets a customizer that receives the default client {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder setClientSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<ThriftRequest>> clientSpanNameExtractorCustomizer) {
    this.clientSpanNameExtractorCustomizer = clientSpanNameExtractorCustomizer;
    return this;
  }

  /**
   * Sets a customizer that receives the default server {@link SpanNameExtractor} and returns a
   * customized one.
   */
  @CanIgnoreReturnValue
  public ThriftTelemetryBuilder setServerSpanNameExtractorCustomizer(
      UnaryOperator<SpanNameExtractor<ThriftRequest>> serverSpanNameExtractorCustomizer) {
    this.serverSpanNameExtractorCustomizer = serverSpanNameExtractorCustomizer;
    return this;
  }

  /** Returns a new instance with the configured settings. */
  public ThriftTelemetry build() {
    Instrumenter<ThriftRequest, ThriftResponse> serverInstrumenter =
        ThriftInstrumenterFactory.createServerInstrumenter(
            openTelemetry,
            serverSpanNameExtractorCustomizer,
            additionalExtractors,
            additionalServerExtractors);
    Instrumenter<ThriftRequest, ThriftResponse> clientInstrumenter =
        ThriftInstrumenterFactory.createClientInstrumenter(
            openTelemetry,
            clientSpanNameExtractorCustomizer,
            additionalExtractors,
            additionalClientExtractors);

    return new ThriftTelemetry(
        serverInstrumenter, clientInstrumenter, openTelemetry.getPropagators());
  }
}
