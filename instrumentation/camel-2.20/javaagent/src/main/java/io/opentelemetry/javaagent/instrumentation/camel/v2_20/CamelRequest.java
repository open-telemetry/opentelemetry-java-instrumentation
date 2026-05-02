/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

@AutoValue
abstract class CamelRequest {

  static CamelRequest create(
      SpanDecorator spanDecorator,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection,
      SpanKind spanKind) {
    return new AutoValue_CamelRequest(spanDecorator, exchange, endpoint, camelDirection, spanKind);
  }

  abstract SpanDecorator getSpanDecorator();

  abstract Exchange getExchange();

  abstract Endpoint getEndpoint();

  abstract CamelDirection getCamelDirection();

  abstract SpanKind getSpanKind();
}
