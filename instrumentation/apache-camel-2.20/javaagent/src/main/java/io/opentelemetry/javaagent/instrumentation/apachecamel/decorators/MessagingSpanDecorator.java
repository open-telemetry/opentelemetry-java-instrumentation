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

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class MessagingSpanDecorator extends BaseSpanDecorator {

  private final String component;

  public MessagingSpanDecorator(String component) {
    this.component = component;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {

    if ("mqtt".equals(component)) {
      return stripSchemeAndOptions(endpoint);
    }
    return getDestination(exchange, endpoint);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    super.pre(span, exchange, endpoint, camelDirection);

    span.setAttribute(SemanticAttributes.MESSAGING_DESTINATION, getDestination(exchange, endpoint));

    String messageId = getMessageId(exchange);
    if (messageId != null) {
      span.setAttribute(SemanticAttributes.MESSAGING_MESSAGE_ID, messageId);
    }
  }

  /**
   * This method identifies the destination from the supplied exchange and/or endpoint.
   *
   * @param exchange The exchange
   * @param endpoint The endpoint
   * @return The message bus destination
   */
  protected String getDestination(Exchange exchange, Endpoint endpoint) {
    switch (component) {
      case "cometds":
      case "cometd":
        return URI.create(endpoint.getEndpointUri()).getPath().substring(1);
      case "rabbitmq":
        return (String) exchange.getIn().getHeader("rabbitmq.EXCHANGE_NAME");
      case "stomp":
        String destination = stripSchemeAndOptions(endpoint);
        if (destination.startsWith("queue:")) {
          destination = destination.substring("queue:".length());
        }
        return destination;
      case "mqtt":
        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        return (queryParameters.containsKey("subscribeTopicNames")
            ? queryParameters.get("subscribeTopicNames")
            : queryParameters.get("publishTopicName"));
      default:
        return stripSchemeAndOptions(endpoint);
    }
  }

  @Override
  public SpanKind getInitiatorSpanKind() {
    switch (component) {
      case "aws-sqs":
        return SpanKind.INTERNAL;
      default:
        return SpanKind.PRODUCER;
    }
  }

  @Override
  public SpanKind getReceiverSpanKind() {
    switch (component) {
      case "aws-sqs":
        return SpanKind.INTERNAL;
      default:
        return SpanKind.CONSUMER;
    }
  }

  /**
   * This method identifies the message id for the messaging exchange.
   *
   * @return The message id, or null if no id exists for the exchange
   */
  protected String getMessageId(Exchange exchange) {
    switch (component) {
      case "aws-sns":
        return (String) exchange.getIn().getHeader("CamelAwsSnsMessageId");
      case "aws-sqs":
        return (String) exchange.getIn().getHeader("CamelAwsSqsMessageId");
      case "ironmq":
        return (String) exchange.getIn().getHeader("CamelIronMQMessageId");
      case "jms":
        return (String) exchange.getIn().getHeader("JMSMessageID");
      default:
        return null;
    }
  }
}
