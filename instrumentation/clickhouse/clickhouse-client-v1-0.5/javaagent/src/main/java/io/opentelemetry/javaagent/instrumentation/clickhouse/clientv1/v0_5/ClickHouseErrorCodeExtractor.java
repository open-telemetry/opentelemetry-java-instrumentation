/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import javax.annotation.Nullable;

final class ClickHouseErrorCodeExtractor {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    String errorCode = getErrorCode(error);
    return "0".equals(errorCode) ? null : errorCode;
  }

  @Nullable
  static String getErrorCode(@Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      return Integer.toString(((ClickHouseException) error).getErrorCode());
    }
    return null;
  }

  private ClickHouseErrorCodeExtractor() {}
}
