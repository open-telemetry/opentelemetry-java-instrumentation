/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import java.util.List;

final class DbClientMetricsAdvice {

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(asList(0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0));

  static final List<Long> RETURNED_ROWS_BUCKETS =
      unmodifiableList(asList(10L, 50L, 100L, 250L, 500L, 1000L, 2000L, 5000L, 10000L));

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                DB_SYSTEM_NAME,
                DB_COLLECTION_NAME,
                DB_NAMESPACE,
                DB_OPERATION_NAME,
                DB_QUERY_SUMMARY,
                ERROR_TYPE,
                NETWORK_PEER_ADDRESS,
                NETWORK_PEER_PORT,
                SERVER_ADDRESS,
                SERVER_PORT));
  }

  static void applyReturnedRowsAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                DB_SYSTEM_NAME,
                DB_COLLECTION_NAME,
                DB_NAMESPACE,
                DB_OPERATION_NAME,
                DB_QUERY_SUMMARY,
                DB_RESPONSE_STATUS_CODE,
                ERROR_TYPE,
                NETWORK_PEER_ADDRESS,
                NETWORK_PEER_PORT,
                SERVER_ADDRESS,
                SERVER_PORT));
  }

  private DbClientMetricsAdvice() {}
}
