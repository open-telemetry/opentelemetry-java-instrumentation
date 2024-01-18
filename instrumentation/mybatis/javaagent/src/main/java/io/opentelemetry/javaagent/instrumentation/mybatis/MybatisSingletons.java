/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class MybatisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.mybatis";

  private static final Instrumenter<MapperMethodRequest, Void> MAPPER_INSTRUMENTER;

  static {
    SpanNameExtractor<MapperMethodRequest> spanNameExtractor = new MybatisSpanNameExtractor();

    MAPPER_INSTRUMENTER =
        Instrumenter.<MapperMethodRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(new MybatisAttributesExtractor())
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<MapperMethodRequest, Void> mapperInstrumenter() {
    return MAPPER_INSTRUMENTER;
  }

  private MybatisSingletons() {}
}
