/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.grpc.Context;
import io.opentelemetry.trace.Span;
import java.util.EventObject;
import org.apache.camel.management.event.ExchangeSendingEvent;
import org.apache.camel.management.event.ExchangeSentEvent;
import org.apache.camel.support.EventNotifierSupport;

final class CamelEventNotifier extends EventNotifierSupport {

  @Override
  public void notify(EventObject event) throws Exception {

    try {
      /** Camel about to send (outbound). */
      if (event instanceof ExchangeSendingEvent) {
        ExchangeSendingEvent ese = (ExchangeSendingEvent) event;
        SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(ese.getEndpoint());
        if (!sd.shouldStartNewSpan()) {
          return;
        }

        Span span =
            CamelTracer.TRACER.startSpan(
                sd.getOperationName(ese.getExchange(), ese.getEndpoint()),
                sd.getInitiatorSpanKind());
        sd.pre(span, ese.getExchange(), ese.getEndpoint(), CamelDirection.OUTBOUND);
        CamelPropagationUtil.injectParent(
            Context.current(), ese.getExchange().getIn().getHeaders());
        ActiveSpanManager.activate(ese.getExchange(), span);

        if (CamelTracer.LOG.isTraceEnabled()) {
          CamelTracer.LOG.trace("Client span started " + span);
        }
        /** Camel finished sending (outbound). Finish span and remove it from CAMEL holder. */
      } else if (event instanceof ExchangeSentEvent) {
        ExchangeSentEvent ese = (ExchangeSentEvent) event;
        SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(ese.getEndpoint());
        if (!sd.shouldStartNewSpan()) {
          return;
        }
        Span span = ActiveSpanManager.getSpan(ese.getExchange());
        if (span != null) {
          if (CamelTracer.LOG.isTraceEnabled()) {
            CamelTracer.LOG.trace("Client span finished " + span);
          }
          sd.post(span, ese.getExchange(), ese.getEndpoint());
          ActiveSpanManager.deactivate(ese.getExchange());
        } else {
          CamelTracer.LOG.warn("Could not find managed span for exchange " + ese.getExchange());
        }
      }
    } catch (Throwable t) {
      CamelTracer.LOG.warn("Failed to capture tracing data", t);
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
