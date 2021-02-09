/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CamelRoutePolicy extends RoutePolicySupport {

  private static final Logger LOG = LoggerFactory.getLogger(CamelRoutePolicy.class);

  private Span spanOnExchangeBegin(
      Route route, Exchange exchange, SpanDecorator sd, SpanKind spanKind) {
    Span activeSpan = Span.current();
    String name = sd.getOperationName(exchange, route.getEndpoint(), CamelDirection.INBOUND);
    SpanBuilder builder = CamelTracer.TRACER.spanBuilder(name);
    builder.setSpanKind(spanKind);
    if (!activeSpan.getSpanContext().isValid()) {
      Context parentContext =
          CamelPropagationUtil.extractParent(exchange.getIn().getHeaders(), route.getEndpoint());
      if (parentContext != null) {
        builder.setParent(parentContext);
      }
    }
    return builder.startSpan();
  }

  private SpanKind spanKind(SpanDecorator sd) {
    Span activeSpan = Span.current();
    // if there's an active span, this is not a root span which we always mark as INTERNAL
    return (activeSpan.getSpanContext().isValid() ? SpanKind.INTERNAL : sd.getReceiverSpanKind());
  }

  /**
   * Route exchange started, ie request could have been already captured by upper layer
   * instrumentation.
   */
  @Override
  public void onExchangeBegin(Route route, Exchange exchange) {
    try {
      SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(route.getEndpoint());
      SpanKind spanKind = spanKind(sd);
      Span span = spanOnExchangeBegin(route, exchange, sd, spanKind);
      sd.pre(span, exchange, route.getEndpoint(), CamelDirection.INBOUND);
      ActiveSpanManager.activate(exchange, span, spanKind);
      LOG.debug("[Route start] Receiver span started {}", span);
    } catch (Throwable t) {
      LOG.warn("Failed to capture tracing data", t);
    }
  }

  /** Route exchange done. Get active CAMEL span, finish, remove from CAMEL holder. */
  @Override
  public void onExchangeDone(Route route, Exchange exchange) {
    try {
      Span span = ActiveSpanManager.getSpan(exchange);
      if (span != null) {

        LOG.debug("[Route finished] Receiver span finished {}", span);
        SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(route.getEndpoint());
        sd.post(span, exchange, route.getEndpoint());
        ActiveSpanManager.deactivate(exchange);
      } else {
        LOG.warn("Could not find managed span for exchange={}", exchange);
      }
    } catch (Throwable t) {
      LOG.warn("Failed to capture tracing data", t);
    }
  }
}
