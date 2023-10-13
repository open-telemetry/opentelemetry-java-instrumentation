/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.semconv.SemanticAttributes;

final class HttpMetricsAdvice {

  static void applyStableClientDurationAdvice(DoubleHistogramBuilder builder) {
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
                SemanticAttributes.SERVER_PORT,
                SemanticAttributes.URL_SCHEME));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyOldClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_METHOD,
                SemanticAttributes.HTTP_STATUS_CODE,
                SemanticAttributes.NET_PEER_NAME,
                SemanticAttributes.NET_PEER_PORT,
                SemanticAttributes.NET_PROTOCOL_NAME,
                SemanticAttributes.NET_PROTOCOL_VERSION,
                SemanticAttributes.NET_SOCK_PEER_ADDR));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (!(builder instanceof ExtendedLongHistogramBuilder)) {
      return;
    }
    ((ExtendedLongHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                // stable attributes
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.HTTP_RESPONSE_STATUS_CODE,
                HttpAttributes.ERROR_TYPE,
                SemanticAttributes.NETWORK_PROTOCOL_NAME,
                SemanticAttributes.NETWORK_PROTOCOL_VERSION,
                SemanticAttributes.SERVER_ADDRESS,
                SemanticAttributes.SERVER_PORT,
                SemanticAttributes.URL_SCHEME,
                // old attributes
                SemanticAttributes.HTTP_METHOD,
                SemanticAttributes.HTTP_STATUS_CODE,
                SemanticAttributes.NET_PEER_NAME,
                SemanticAttributes.NET_PEER_PORT,
                SemanticAttributes.NET_PROTOCOL_NAME,
                SemanticAttributes.NET_PROTOCOL_VERSION,
                SemanticAttributes.NET_SOCK_PEER_ADDR));
  }

  static void applyStableServerDurationAdvice(DoubleHistogramBuilder builder) {
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

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyOldServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAttributesAdvice(
            asList(
                SemanticAttributes.HTTP_SCHEME,
                SemanticAttributes.HTTP_ROUTE,
                SemanticAttributes.HTTP_METHOD,
                SemanticAttributes.HTTP_STATUS_CODE,
                SemanticAttributes.NET_HOST_NAME,
                SemanticAttributes.NET_HOST_PORT,
                SemanticAttributes.NET_PROTOCOL_NAME,
                SemanticAttributes.NET_PROTOCOL_VERSION));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
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
                SemanticAttributes.URL_SCHEME,
                // old attributes
                SemanticAttributes.HTTP_SCHEME,
                SemanticAttributes.HTTP_ROUTE,
                SemanticAttributes.HTTP_METHOD,
                SemanticAttributes.HTTP_STATUS_CODE,
                SemanticAttributes.NET_HOST_NAME,
                SemanticAttributes.NET_HOST_PORT,
                SemanticAttributes.NET_PROTOCOL_NAME,
                SemanticAttributes.NET_PROTOCOL_VERSION));
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  static void applyServerActiveRequestsAdvice(LongUpDownCounterBuilder builder) {
    if (!(builder instanceof ExtendedLongUpDownCounterBuilder)) {
      return;
    }
    ((ExtendedLongUpDownCounterBuilder) builder)
        .setAttributesAdvice(
            asList(
                // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md#metric-httpserveractive_requests
                SemanticAttributes.HTTP_METHOD,
                SemanticAttributes.HTTP_SCHEME,
                SemanticAttributes.NET_HOST_NAME,
                SemanticAttributes.NET_HOST_PORT,
                // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserveractive_requests
                SemanticAttributes.HTTP_REQUEST_METHOD,
                SemanticAttributes.URL_SCHEME));
  }

  private HttpMetricsAdvice() {}
}
