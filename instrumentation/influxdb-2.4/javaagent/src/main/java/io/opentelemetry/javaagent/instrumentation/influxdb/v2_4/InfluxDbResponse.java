/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import javax.annotation.Nullable;
import org.influxdb.dto.QueryResult;

final class InfluxDbResponse {
  @Nullable private final String errorType;
  @Nullable private final String statusCode;

  InfluxDbResponse(@Nullable String errorType, @Nullable String statusCode) {
    this.errorType = errorType;
    this.statusCode = statusCode;
  }

  @Nullable
  String getErrorType() {
    return errorType;
  }

  @Nullable
  String getStatusCode() {
    return statusCode;
  }

  static boolean hasError(@Nullable QueryResult result) {
    if (result == null) {
      return false;
    }
    // The JSON chunk processor uses a top-level "DONE" error to signal EOF.
    if (result.hasError() && !"DONE".equals(result.getError())) {
      return true;
    }
    if (result.getResults() != null) {
      for (QueryResult.Result statement : result.getResults()) {
        if (statement != null && statement.hasError()) {
          return true;
        }
      }
    }
    return false;
  }
}
