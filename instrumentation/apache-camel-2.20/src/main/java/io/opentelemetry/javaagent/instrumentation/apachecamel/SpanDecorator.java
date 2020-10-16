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
import io.opentelemetry.trace.Span;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

/** This interface represents a decorator specific to the component/endpoint being instrumented. */
public interface SpanDecorator {

  /**
   * This method indicates whether the component associated with the SpanDecorator should result in
   * a new span being created.
   *
   * @return Whether a new span should be created
   */
  boolean shouldStartNewSpan();

  /**
   * This method returns the operation name to use with the Span representing this exchange and
   * endpoint.
   *
   * @param exchange The exchange
   * @param endpoint The endpoint
   * @return The operation name
   */
  String getOperationName(Exchange exchange, Endpoint endpoint);

  /**
   * This method adds appropriate details (tags/logs) to the supplied span based on the pre
   * processing of the exchange.
   *
   * @param span The span
   * @param exchange The exchange
   * @param endpoint The endpoint
   */
  void pre(Span span, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection);

  /**
   * This method adds appropriate details (tags/logs) to the supplied span based on the post
   * processing of the exchange.
   *
   * @param span The span
   * @param exchange The exchange
   * @param endpoint The endpoint
   */
  void post(Span span, Exchange exchange, Endpoint endpoint);

  /**
   * This method returns the 'span.kind' value for use when the component is initiating a
   * communication.
   *
   * @return The kind
   */
  Span.Kind getInitiatorSpanKind();

  /**
   * This method returns the 'span.kind' value for use when the component is receiving a
   * communication.
   *
   * @return The kind
   */
  Span.Kind getReceiverSpanKind();
}
