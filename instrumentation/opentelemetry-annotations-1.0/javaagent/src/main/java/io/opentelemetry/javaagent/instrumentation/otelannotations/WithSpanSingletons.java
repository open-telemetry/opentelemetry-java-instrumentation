/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.otelannotations;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.annotation.support.MethodSpanAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class WithSpanSingletons {
  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.opentelemetry-annotations-1.0";

  private static final Instrumenter<WithSpanMethodRequest, Object> INSTRUMENTER =
      createInstrumenter();
  private static final Instrumenter<WithSpanMethodRequest, Object> INSTRUMENTER_WITH_ATTRIBUTES =
      createInstrumenterWithAttributes();

  public static Instrumenter<WithSpanMethodRequest, Object> instrumenter() {
    return INSTRUMENTER;
  }

  public static Instrumenter<WithSpanMethodRequest, Object> instrumenterWithAttributes() {
    return INSTRUMENTER_WITH_ATTRIBUTES;
  }

  private static Instrumenter<WithSpanMethodRequest, Object> createInstrumenter() {
    return Instrumenter.newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, WithSpanMethodRequest::name)
        .newInstrumenter(WithSpanMethodRequest::kind);
  }

  private static Instrumenter<WithSpanMethodRequest, Object> createInstrumenterWithAttributes() {
    return Instrumenter.newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, WithSpanMethodRequest::name)
        .addAttributesExtractor(
            MethodSpanAttributesExtractor.newInstance(
                WithSpanParameterAttributeNamesExtractor.INSTANCE))
        .newInstrumenter(WithSpanMethodRequest::kind);
  }
}
