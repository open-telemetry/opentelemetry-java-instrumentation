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

import io.opentelemetry.context.Context;
import io.opentelemetry.trace.Span;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CamelRoutePolicy extends RoutePolicySupport {

  private static final Logger LOG = LoggerFactory.getLogger(CamelRoutePolicy.class);

  private Span spanOnExchangeBegin(Route route, Exchange exchange, SpanDecorator sd) {
    Span activeSpan = CamelTracer.TRACER.getCurrentSpan();
    String name = sd.getOperationName(exchange, route.getEndpoint(), CamelDirection.INBOUND);
    Span.Builder builder = CamelTracer.TRACER.spanBuilder(name);
    if (!activeSpan.getSpanContext().isValid()) {
      // root operation, set kind, otherwise - INTERNAL
      builder.setSpanKind(sd.getReceiverSpanKind());
      Context parentContext = CamelPropagationUtil.extractParent(exchange.getIn().getHeaders());
      if (parentContext != null) {
        builder.setParent(parentContext);
      }
    }
    return builder.startSpan();
  }

  /**
   * Route exchange started, ie request could have been already captured by upper layer
   * instrumentation.
   */
  @Override
  public void onExchangeBegin(Route route, Exchange exchange) {
    try {
      SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(route.getEndpoint());
      Span span = spanOnExchangeBegin(route, exchange, sd);
      sd.pre(span, exchange, route.getEndpoint(), CamelDirection.INBOUND);
      ActiveSpanManager.activate(exchange, span);
      if (LOG.isTraceEnabled()) {
        LOG.trace("[Route start] Receiver span started " + span);
      }
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
        if (LOG.isTraceEnabled()) {
          LOG.trace("[Route finished] Receiver span finished " + span);
        }
        SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(route.getEndpoint());
        sd.post(span, exchange, route.getEndpoint());
        ActiveSpanManager.deactivate(exchange);
      } else {
        LOG.warn("Could not find managed span for exchange=" + exchange);
      }
    } catch (Throwable t) {
      LOG.warn("Failed to capture tracing data", t);
    }
  }
}
