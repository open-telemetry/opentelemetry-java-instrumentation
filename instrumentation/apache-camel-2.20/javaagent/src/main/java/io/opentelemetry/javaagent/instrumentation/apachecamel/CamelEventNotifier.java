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

import io.opentelemetry.context.Context;
import java.util.EventObject;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

final class CamelEventNotifier extends EventNotifierSupport {

  private static final Logger logger = Logger.getLogger(CamelEventNotifier.class.getName());

  @Override
  public void notify(EventObject event) {
    if (event instanceof ExchangeSendingEvent) {
      onExchangeSending((ExchangeSendingEvent) event);
    } else if (event instanceof ExchangeSentEvent) {
      onExchangeSent((ExchangeSentEvent) event);
    }
  }

  /** Camel about to send (outbound). */
  private static void onExchangeSending(ExchangeSendingEvent ese) {
    SpanDecorator sd = getSpanDecorator(ese.getEndpoint());
    if (!sd.shouldStartNewSpan()) {
      return;
    }

    CamelRequest request =
        CamelRequest.create(
            sd,
            ese.getExchange(),
            ese.getEndpoint(),
            CamelDirection.OUTBOUND,
            sd.getInitiatorSpanKind());
    Context context = startOnExchangeSending(request);

    ActiveContextManager.activate(context, request);
    CamelPropagationUtil.injectParent(context, ese.getExchange().getIn().getHeaders());

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("[Exchange sending] Initiator span started: " + context);
    }
  }

  private static Context startOnExchangeSending(CamelRequest request) {
    Context parentContext = Context.current();
    if (!instrumenter().shouldStart(parentContext, request)) {
      return null;
    }
    return instrumenter().start(parentContext, request);
  }

  /** Camel finished sending (outbound). Finish span and remove it from CAMEL holder. */
  private static void onExchangeSent(ExchangeSentEvent event) {
    SpanDecorator sd = getSpanDecorator(event.getEndpoint());
    if (!sd.shouldStartNewSpan()) {
      return;
    }

    Context context = ActiveContextManager.deactivate(event.getExchange());
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("[Exchange sent] Initiator span finished: " + context);
    }
  }

  @Override
  public boolean isEnabled(EventObject event) {
    return event instanceof ExchangeSendingEvent || event instanceof ExchangeSentEvent;
  }

  @Override
  public String toString() {
    return "OpenTelemetryCamelEventNotifier";
  }
}
