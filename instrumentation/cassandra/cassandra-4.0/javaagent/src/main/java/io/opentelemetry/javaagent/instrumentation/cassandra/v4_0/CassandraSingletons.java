/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.cassandra.v4_0.CassandraRequest;
import io.opentelemetry.instrumentation.cassandra.v4_0.CassandraTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;

public final class CassandraSingletons {
  // using ExecutionInfo because we can get that from ResultSet, AsyncResultSet and DriverException
  private static final Instrumenter<CassandraRequest, ExecutionInfo> INSTRUMENTER;

  static {
    CassandraTelemetry telemetry =
        CassandraTelemetry.builder(GlobalOpenTelemetry.get())
            .setInstrumentationName("io.opentelemetry.cassandra-4.0")
            .setStatementSanitizationEnabled(CommonConfig.get().isStatementSanitizationEnabled())
            .build();

    INSTRUMENTER = telemetry.getInstrumenter();
  }

  public static Instrumenter<CassandraRequest, ExecutionInfo> instrumenter() {
    return INSTRUMENTER;
  }

  private CassandraSingletons() {}
}
