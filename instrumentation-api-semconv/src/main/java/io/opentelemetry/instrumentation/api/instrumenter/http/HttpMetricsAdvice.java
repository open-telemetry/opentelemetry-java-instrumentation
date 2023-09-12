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
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

final class HttpMetricsAdvice {

  static void applyStableClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (builder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          HttpAttributes.HTTP_REQUEST_METHOD,
                          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                          NetworkAttributes.NETWORK_PROTOCOL_NAME,
                          NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                          NetworkAttributes.SERVER_ADDRESS,
                          NetworkAttributes.SERVER_PORT,
                          NetworkAttributes.SERVER_SOCKET_ADDRESS)));
    }
  }

  static void applyOldClientDurationAdvice(DoubleHistogramBuilder builder) {
    if (builder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          SemanticAttributes.HTTP_METHOD,
                          SemanticAttributes.HTTP_STATUS_CODE,
                          SemanticAttributes.NET_PEER_NAME,
                          SemanticAttributes.NET_PEER_PORT,
                          SemanticAttributes.NET_PROTOCOL_NAME,
                          SemanticAttributes.NET_PROTOCOL_VERSION,
                          SemanticAttributes.NET_SOCK_PEER_ADDR)));
    }
  }

  static void applyClientRequestSizeAdvice(LongHistogramBuilder builder) {
    if (builder instanceof ExtendedLongHistogramBuilder) {
      ((ExtendedLongHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          // stable attributes
                          HttpAttributes.HTTP_REQUEST_METHOD,
                          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                          NetworkAttributes.NETWORK_PROTOCOL_NAME,
                          NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                          NetworkAttributes.SERVER_ADDRESS,
                          NetworkAttributes.SERVER_PORT,
                          NetworkAttributes.SERVER_SOCKET_ADDRESS,
                          // old attributes
                          SemanticAttributes.HTTP_METHOD,
                          SemanticAttributes.HTTP_STATUS_CODE,
                          SemanticAttributes.NET_PEER_NAME,
                          SemanticAttributes.NET_PEER_PORT,
                          SemanticAttributes.NET_PROTOCOL_NAME,
                          SemanticAttributes.NET_PROTOCOL_VERSION,
                          SemanticAttributes.NET_SOCK_PEER_ADDR)));
    }
  }

  static void applyStableServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (builder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          SemanticAttributes.HTTP_ROUTE,
                          HttpAttributes.HTTP_REQUEST_METHOD,
                          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                          NetworkAttributes.NETWORK_PROTOCOL_NAME,
                          NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                          UrlAttributes.URL_SCHEME)));
    }
  }

  static void applyOldServerDurationAdvice(DoubleHistogramBuilder builder) {
    if (builder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          SemanticAttributes.HTTP_SCHEME,
                          SemanticAttributes.HTTP_ROUTE,
                          SemanticAttributes.HTTP_METHOD,
                          SemanticAttributes.HTTP_STATUS_CODE,
                          SemanticAttributes.NET_HOST_NAME,
                          SemanticAttributes.NET_HOST_PORT,
                          SemanticAttributes.NET_PROTOCOL_NAME,
                          SemanticAttributes.NET_PROTOCOL_VERSION)));
    }
  }

  static void applyServerRequestSizeAdvice(LongHistogramBuilder builder) {
    if (builder instanceof ExtendedLongHistogramBuilder) {
      ((ExtendedLongHistogramBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          // stable attributes
                          SemanticAttributes.HTTP_ROUTE,
                          HttpAttributes.HTTP_REQUEST_METHOD,
                          HttpAttributes.HTTP_RESPONSE_STATUS_CODE,
                          NetworkAttributes.NETWORK_PROTOCOL_NAME,
                          NetworkAttributes.NETWORK_PROTOCOL_VERSION,
                          UrlAttributes.URL_SCHEME,
                          // old attributes
                          SemanticAttributes.HTTP_SCHEME,
                          SemanticAttributes.HTTP_ROUTE,
                          SemanticAttributes.HTTP_METHOD,
                          SemanticAttributes.HTTP_STATUS_CODE,
                          SemanticAttributes.NET_HOST_NAME,
                          SemanticAttributes.NET_HOST_PORT,
                          SemanticAttributes.NET_PROTOCOL_NAME,
                          SemanticAttributes.NET_PROTOCOL_VERSION)));
    }
  }

  static void applyServerActiveRequestsAdvice(LongUpDownCounterBuilder builder) {
    if (builder instanceof ExtendedLongUpDownCounterBuilder) {
      ((ExtendedLongUpDownCounterBuilder) builder)
          .setAdvice(
              advice ->
                  advice.setAttributes(
                      asList(
                          // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md#metric-httpserveractive_requests
                          SemanticAttributes.HTTP_METHOD,
                          SemanticAttributes.HTTP_SCHEME,
                          SemanticAttributes.NET_HOST_NAME,
                          SemanticAttributes.NET_HOST_PORT,
                          // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserveractive_requests
                          HttpAttributes.HTTP_REQUEST_METHOD,
                          UrlAttributes.URL_SCHEME)));
    }
  }

  private HttpMetricsAdvice() {}
}
