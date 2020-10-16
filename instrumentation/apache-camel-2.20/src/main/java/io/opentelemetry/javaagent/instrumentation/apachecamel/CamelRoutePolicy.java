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
import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.support.RoutePolicySupport;

final class CamelRoutePolicy extends RoutePolicySupport {

  private Span spanOnExchangeBegin(Route route, Exchange exchange, SpanDecorator sd) {
    Span activeSpan = CamelTracer.TRACER.getCurrentSpan();
    Span.Builder builder =
        CamelTracer.TRACER.spanBuilder(sd.getOperationName(exchange, route.getEndpoint()));
    if (!activeSpan.getContext().isValid()) {
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
      if (CamelTracer.LOG.isTraceEnabled()) {
        CamelTracer.LOG.trace("Span started " + span);
      }
    } catch (Throwable t) {
      CamelTracer.LOG.warn("Failed to capture tracing data", t);
    }
  }

  /** Route exchange done. Get active CAMEL span, finish, remove from CAMEL holder. */
  @Override
  public void onExchangeDone(Route route, Exchange exchange) {
    try {
      Span span = ActiveSpanManager.getSpan(exchange);
      if (span != null) {
        if (CamelTracer.LOG.isTraceEnabled()) {
          CamelTracer.LOG.trace("Server span finished " + span);
        }
        SpanDecorator sd = CamelTracer.TRACER.getSpanDecorator(route.getEndpoint());
        sd.post(span, exchange, route.getEndpoint());
        ActiveSpanManager.deactivate(exchange);
      } else {
        CamelTracer.LOG.warn("Could not find managed span for exchange=" + exchange);
      }
    } catch (Throwable t) {
      CamelTracer.LOG.warn("Failed to capture tracing data", t);
    }
  }
}
