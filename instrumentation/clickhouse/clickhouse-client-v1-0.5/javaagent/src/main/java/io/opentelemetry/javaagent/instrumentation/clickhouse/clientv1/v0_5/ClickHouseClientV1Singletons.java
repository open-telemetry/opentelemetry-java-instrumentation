/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME, ClickHouseClientV1Singletons::getErrorCode);
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  private static Integer getErrorCode(@Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      return ((ClickHouseException) error).getErrorCode();
    }
    return null;
  }

  private ClickHouseClientV1Singletons() {}
}
