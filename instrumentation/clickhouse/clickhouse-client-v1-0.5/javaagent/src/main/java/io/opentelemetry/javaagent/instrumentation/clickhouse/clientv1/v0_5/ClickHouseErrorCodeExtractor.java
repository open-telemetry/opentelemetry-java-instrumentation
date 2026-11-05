/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

final class ClickHouseErrorCodeExtractor {

  @Nullable
  static String getErrorType(@Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      int errorCode = ((ClickHouseException) error).getErrorCode();
      return errorCode == 0 ? null : Integer.toString(errorCode);
    }
    return null;
  }

  @Nullable
  static String getResponseStatusCode(@Nullable Throwable error) {
    if (error instanceof ClickHouseException) {
      ClickHouseException clickHouseException = (ClickHouseException) error;
      int errorCode = clickHouseException.getErrorCode();
      if (errorCode == 0 || isClientGeneratedCode(clickHouseException, errorCode)) {
        return null;
      }
      return Integer.toString(errorCode);
    }
    return null;
  }

  private static boolean isClientGeneratedCode(ClickHouseException error, int errorCode) {
    Throwable cause = error.getCause();
    if (cause == null) {
      return false;
    }

    if (cause instanceof ConnectException
        || cause instanceof UnknownHostException
        || cause instanceof SocketTimeoutException
        || cause instanceof TimeoutException
        || cause instanceof CancellationException
        || cause instanceof InterruptedException) {
      return true;
    }

    if (errorCode == ClickHouseException.ERROR_ABORTED
        || errorCode == ClickHouseException.ERROR_UNKNOWN) {
      String causeMessage = cause.getMessage();
      return causeMessage == null || !causeMessage.contains("Code: " + errorCode);
    }
    return false;
  }

  private ClickHouseErrorCodeExtractor() {}
}
