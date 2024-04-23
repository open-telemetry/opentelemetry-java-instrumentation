/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
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
                HttpAttributes.HTTP_REQUEST_METHOD,
                HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                ErrorAttributes.ERROR_TYPE,
                NetworkAttributes.NETWORK_PROTOCOL_NAME,
                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                ServerAttributes.SERVER_ADDRESS,
                ServerAttributes.SERVER_PORT));
  }

  static void applyServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                HttpAttributes.HTTP_ROUTE,
                HttpAttributes.HTTP_REQUEST_METHOD,
                HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                ErrorAttributes.ERROR_TYPE,
                NetworkAttributes.NETWORK_PROTOCOL_NAME,
                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                UrlAttributes.URL_SCHEME));
  }

  private HttpMetricsAdvice() {}
}
