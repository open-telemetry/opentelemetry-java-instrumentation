/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.List;

final class HttpMetricsAdvice {

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0));

  static void applyClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
  }

  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
  }

  static void applyServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_ROUTE,
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.URL_SCHEME));
  }

  static void applyServerRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                // stable attributes
                SemanticAttributes.HTTP_ROUTE,
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.URL_SCHEME));
  }

  static void applyServerActiveRequestsAdvice(LongUpDownCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongUpDownCounterBuilder)) {
      return;
    }
    ((ExtendedLongUpDownCounterBuilder) builder)
        .setAttributesAdvice(
            asList(
                // https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-metrics.md#metric-httpserveractive_requests
                SemanticAttributes.HTTP_REQUEST_METHOD, SemanticAttributes.URL_SCHEME));
  }

  private HttpMetricsAdvice() {}
}
