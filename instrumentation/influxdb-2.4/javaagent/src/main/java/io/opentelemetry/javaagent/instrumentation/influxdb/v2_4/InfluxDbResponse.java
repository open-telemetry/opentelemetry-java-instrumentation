/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

import javax.annotation.Nullable;
import org.influxdb.dto.QueryResult;

final class InfluxDbResponse {
  @Nullable private final String errorType;

  InfluxDbResponse(@Nullable String errorType) {
    this.errorType = errorType;
  }

  @Nullable
  String getErrorType() {
    return errorType;
  }

  static InfluxDbResponse from(@Nullable QueryResult result) {
    return new InfluxDbResponse(hasError(result) ? "_OTHER" : null);
  }

  static boolean hasError(@Nullable QueryResult result) {
    if (result == null) {
      return false;
    }
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
