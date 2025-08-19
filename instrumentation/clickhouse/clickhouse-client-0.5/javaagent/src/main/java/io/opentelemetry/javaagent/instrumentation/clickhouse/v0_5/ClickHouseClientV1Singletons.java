/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.v0_5;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.common.ClickHouseInstrumenterFactory;

public final class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-0.5";
  private static final Instrumenter<ClickHouseDbRequest, Void> INSTRUMENTER;

  static {
    INSTRUMENTER = ClickHouseInstrumenterFactory.createInstrumenter(INSTRUMENTER_NAME);
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private ClickHouseClientV1Singletons() {}
}
