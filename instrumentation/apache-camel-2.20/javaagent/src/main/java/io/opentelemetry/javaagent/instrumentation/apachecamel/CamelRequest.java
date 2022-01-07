/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.trace.SpanKind;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

@AutoValue
abstract class CamelRequest {

  public static CamelRequest create(
      SpanDecorator spanDecorator,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection,
      SpanKind spanKind) {
    return new AutoValue_CamelRequest(spanDecorator, exchange, endpoint, camelDirection, spanKind);
  }

  public abstract SpanDecorator getSpanDecorator();

  public abstract Exchange getExchange();

  public abstract Endpoint getEndpoint();

  public abstract CamelDirection getCamelDirection();

  public abstract SpanKind getSpanKind();
}
