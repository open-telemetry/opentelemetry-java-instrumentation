/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import javax.sql.DataSource;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DataSourceInstrumenterFactory {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";
  private static final DataSourceCodeAttributesGetter codeAttributesGetter =
      new DataSourceCodeAttributesGetter();

  public static Instrumenter<DataSource, Void> createInstrumenter(OpenTelemetry openTelemetry) {
    return Instrumenter.<DataSource, Void>builder(
            openTelemetry, INSTRUMENTATION_NAME, CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .buildInstrumenter();
  }

  private DataSourceInstrumenterFactory() {}
}
