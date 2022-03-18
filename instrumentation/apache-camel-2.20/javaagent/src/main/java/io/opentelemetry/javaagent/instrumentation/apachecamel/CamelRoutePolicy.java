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

import static io.opentelemetry.javaagent.instrumentation.apachecamel.CamelSingletons.getSpanDecorator;
import static io.opentelemetry.javaagent.instrumentation.apachecamel.CamelSingletons.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

final class CamelRoutePolicy extends RoutePolicySupport {

  private static final Logger logger = Logger.getLogger(CamelRoutePolicy.class.getName());

  private static Context spanOnExchangeBegin(
      Route route, Exchange exchange, SpanDecorator sd, Context parentContext) {
    Span activeSpan = Span.fromContext(parentContext);
    if (!activeSpan.getSpanContext().isValid()) {
      parentContext =
          CamelPropagationUtil.extractParent(exchange.getIn().getHeaders(), route.getEndpoint());
    }

    SpanKind spanKind = spanKind(activeSpan, sd);
    CamelRequest request =
        CamelRequest.create(sd, exchange, route.getEndpoint(), CamelDirection.INBOUND, spanKind);
    sd.updateServerSpanName(parentContext, exchange, route.getEndpoint(), CamelDirection.INBOUND);

    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    Context context = instrumenter().start(parentContext, request);
    ActiveContextManager.activate(context, request);
    return context;
  }

  private static SpanKind spanKind(Span activeSpan, SpanDecorator sd) {
    // if there's an active span, this is not a root span which we always mark as INTERNAL
    return activeSpan.getSpanContext().isValid() ? SpanKind.INTERNAL : sd.getReceiverSpanKind();
  }

  /**
   * Route exchange started, ie request could have been already captured by upper layer
   * instrumentation.
   */
  @Override
  public void onExchangeBegin(Route route, Exchange exchange) {
    SpanDecorator sd = getSpanDecorator(route.getEndpoint());
    Context parentContext = Context.current();
    Context context = spanOnExchangeBegin(route, exchange, sd, parentContext);
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("[Route start] Receiver span started " + context);
    }
  }

  /** Route exchange done. Get active CAMEL span, finish, remove from CAMEL holder. */
  @Override
  public void onExchangeDone(Route route, Exchange exchange) {
    Context context = ActiveContextManager.deactivate(exchange);
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("[Route finished] Receiver span finished " + context);
    }
  }
}
