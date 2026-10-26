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
    return getNumericErrorCode(error);
  }

  @Nullable
  private static String getNumericErrorCode(@Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      int errorCode = ((ClickHouseException) error).getErrorCode();
      return errorCode == 0 ? null : Integer.toString(errorCode);
    }
    return null;
  }

  @Nullable
  static String getDbResponseStatusCode(@Nullable Throwable error) {
    return getNumericErrorCode(error);
  }

  private ClickHouseErrorCodeExtractor() {}
}
