/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public final class MyBatisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.mybatis-3.2";

  private static final Instrumenter<MapperMethodRequest, Void> MAPPER_INSTRUMENTER;

  static {
    SpanNameExtractor<MapperMethodRequest> spanNameExtractor = new MyBatisSpanNameExtractor();

    MAPPER_INSTRUMENTER =
        Instrumenter.<MapperMethodRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .buildInstrumenter(SpanKindExtractor.alwaysInternal());
  }

  public static Instrumenter<MapperMethodRequest, Void> mapperInstrumenter() {
    return MAPPER_INSTRUMENTER;
  }

  private MyBatisSingletons() {}
}
