/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JdbcInstrumenterFactory {
  public static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";
  private static final JdbcAttributesGetter dbAttributesGetter = new JdbcAttributesGetter();
  private static final JdbcNetworkAttributesGetter netAttributesGetter =
      new JdbcNetworkAttributesGetter();

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter() {
    return createStatementInstrumenter(GlobalOpenTelemetry.get());
  }

  public static Instrumenter<DbRequest, Void> createStatementInstrumenter(
      OpenTelemetry openTelemetry) {
    return Instrumenter.<DbRequest, Void>builder(
            openTelemetry,
            INSTRUMENTATION_NAME,
            DbClientSpanNameExtractor.create(dbAttributesGetter))
        .addAttributesExtractor(
            SqlClientAttributesExtractor.builder(dbAttributesGetter)
                .setStatementSanitizationEnabled(
                    ConfigPropertiesUtil.getBoolean(
                        "otel.instrumentation.common.db-statement-sanitizer.enabled", true))
                .build())
        .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
        .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  private JdbcInstrumenterFactory() {}
}
