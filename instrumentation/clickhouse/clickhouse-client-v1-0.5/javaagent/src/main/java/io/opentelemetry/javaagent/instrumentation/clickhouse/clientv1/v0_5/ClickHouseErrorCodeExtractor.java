/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
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
  static String getDbResponseStatusCode(@Nullable Throwable error) {
    if (!(error instanceof ClickHouseException)) {
      return null;
    }
    Throwable cause = error.getCause();
    if (cause != null) {
      // V1 also assigns server-like codes to client failures. Server responses wrapped in
      // exceptions retain the server's "Code:" message, unlike transport and cancellation errors.
      if (cause instanceof ConnectException
          || cause instanceof UnknownHostException
          || cause instanceof InterruptedIOException
          || cause instanceof TimeoutException
          || cause instanceof CancellationException
          || cause instanceof InterruptedException) {
        return null;
      }
      String message = cause.getMessage();
      if (message == null
          || !(message.startsWith("Code:") || message.startsWith("Poco::Exception. Code: "))) {
        return null;
      }
    }
    return getErrorType(error);
  }

  private ClickHouseErrorCodeExtractor() {}
}
