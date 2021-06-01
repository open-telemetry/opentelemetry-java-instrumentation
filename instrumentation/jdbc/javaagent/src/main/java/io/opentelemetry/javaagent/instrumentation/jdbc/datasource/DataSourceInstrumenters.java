/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.datasource;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import javax.sql.DataSource;

public final class DataSourceInstrumenters {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.javaagent.jdbc";

  private static final Instrumenter<DataSource, Void> INSTRUMENTER;

  static {
    CodeAttributesExtractor<DataSource, Void> attributesExtractor =
        new DataSourceCodeAttributesExtractor();
    SpanNameExtractor<DataSource> spanNameExtractor =
        CodeSpanNameExtractor.create(attributesExtractor);

    INSTRUMENTER =
        Instrumenter.<DataSource, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .newInstrumenter();
  }

  public static Instrumenter<DataSource, Void> instrumenter() {
    return INSTRUMENTER;
  }
}
