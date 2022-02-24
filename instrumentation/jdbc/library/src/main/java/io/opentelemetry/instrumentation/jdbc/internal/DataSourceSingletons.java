/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import javax.sql.DataSource;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DataSourceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";

  private static final Instrumenter<DataSource, Void> INSTRUMENTER;

  static {
    DataSourceCodeAttributesGetter codeAttributesGetter = new DataSourceCodeAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<DataSource, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .newInstrumenter();
  }

  public static Instrumenter<DataSource, Void> instrumenter() {
    return INSTRUMENTER;
  }
}
