/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbcmysql.v0_8;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcNetAttributesGetter;

public class R2dbcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.r2dbc";

  private static final Instrumenter<DbRequest, Void> INSTRUMENTER;

  static {
    JdbcAttributesGetter dbAttributesGetter = new JdbcAttributesGetter();
    JdbcNetAttributesGetter netAttributesGetter = new JdbcNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<DbRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(SqlClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<DbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private R2dbcSingletons() {}
}
