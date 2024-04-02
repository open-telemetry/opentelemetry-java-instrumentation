/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.http;

import static java.util.Arrays.asList;

import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.semconv.UrlAttributes;

final class HttpExperimentalMetricsAdvice {

  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                SemanticAttributes.ERROR_TYPE,
                NetworkAttributes.NETWORK_PROTOCOL_NAME,
                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT));
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
                SemanticAttributes.ERROR_TYPE,
                NetworkAttributes.NETWORK_PROTOCOL_NAME,
                NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                UrlAttributes.URL_SCHEME));
  }

  static void applyServerActiveRequestsAdvice(LongUpDownCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongUpDownCounterBuilder)) {
      return;
    }
    ((ExtendedLongUpDownCounterBuilder) builder)
        .setAttributesAdvice(
            asList(
                // https://github.com/open-telemetry/semantic-conventions/blob/v1.23.0/docs/http/http-metrics.md#metric-httpserveractive_requests
                SemanticAttributes.HTTP_REQUEST_METHOD, UrlAttributes.URL_SCHEME));
  }

  private HttpExperimentalMetricsAdvice() {}
}
