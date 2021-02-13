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
import io.opentelemetry.context.Context;
import java.util.EventObject;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CamelEventNotifier extends EventNotifierSupport {

  private static final Logger LOG = LoggerFactory.getLogger(CamelEventNotifier.class);

  @Override
  public void notify(EventObject event) {

    try {
      if (event instanceof ExchangeSendingEvent) {
        onExchangeSending((ExchangeSendingEvent) event);
      } else if (event instanceof ExchangeSentEvent) {
        onExchangeSent((ExchangeSentEvent) event);
      }
    } catch (Throwable t) {
      LOG.warn("Failed to capture tracing data", t);
    }
  }

  /** Camel about to send (outbound). */
  private void onExchangeSending(ExchangeSendingEvent ese) {
    SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(ese.getEndpoint());
    if (!sd.shouldStartNewSpan()) {
      return;
    }

    String name =
        sd.getOperationName(ese.getExchange(), ese.getEndpoint(), CamelDirection.OUTBOUND);
    Context context = CamelTracer.TRACER.startSpan(name, sd.getInitiatorSpanKind());
    Span span = Span.fromContext(context);
    sd.pre(span, ese.getExchange(), ese.getEndpoint(), CamelDirection.OUTBOUND);
    ActiveSpanManager.activate(ese.getExchange(), span, sd.getInitiatorSpanKind());
    CamelPropagationUtil.injectParent(context, ese.getExchange().getIn().getHeaders());

    LOG.debug("[Exchange sending] Initiator span started: {}", span);
  }

  /** Camel finished sending (outbound). Finish span and remove it from CAMEL holder. */
  private void onExchangeSent(ExchangeSentEvent event) {
    ExchangeSentEvent ese = event;
    SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(ese.getEndpoint());
    if (!sd.shouldStartNewSpan()) {
      return;
    }

    Span span = ActiveSpanManager.getSpan(ese.getExchange());
    if (span != null) {
      LOG.debug("[Exchange sent] Initiator span finished: {}", span);
      sd.post(span, ese.getExchange(), ese.getEndpoint());
      ActiveSpanManager.deactivate(ese.getExchange());
    } else {
      LOG.warn("Could not find managed span for exchange: {}", ese.getExchange());
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
