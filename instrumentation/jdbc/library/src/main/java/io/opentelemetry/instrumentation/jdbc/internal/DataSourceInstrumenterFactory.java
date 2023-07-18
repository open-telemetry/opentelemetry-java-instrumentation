/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.sql.DataSource;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DataSourceInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";

  public static Instrumenter<DataSource, DbInfo> createDataSourceInstrumenter(
      OpenTelemetry openTelemetry) {
    DataSourceCodeAttributesGetter getter = DataSourceCodeAttributesGetter.INSTANCE;
    return Instrumenter.<DataSource, DbInfo>builder(
            openTelemetry, INSTRUMENTATION_NAME, CodeSpanNameExtractor.create(getter))
        .addAttributesExtractor(CodeAttributesExtractor.create(getter))
        .addAttributesExtractor(DataSourceDbAttributesExtractor.INSTANCE)
        .buildInstrumenter();
  }

  private DataSourceInstrumenterFactory() {}
}
