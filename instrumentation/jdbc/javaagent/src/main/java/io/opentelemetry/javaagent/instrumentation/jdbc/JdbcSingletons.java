/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcAttributesGetter;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcNetAttributesGetter;

public final class JdbcSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jdbc";

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
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<DbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private JdbcSingletons() {}
}
