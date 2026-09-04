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

package io.opentelemetry.javaagent.instrumentation.camel.v2_20.decorators;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelDirection;
import java.net.URI;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

public class MessagingSpanDecorator extends BaseSpanDecorator {

  private final String component;
  private final String system;
  private final boolean spanContextPropagated;
  private final String sendOperationName;

  static MessagingSpanDecorator create(String component) {
    return create(component, component);
  }

  static MessagingSpanDecorator create(String component, String system) {
    return create(component, system, true);
  }

  static MessagingSpanDecorator create(
      String component, String system, boolean spanContextPropagated) {
    return new MessagingSpanDecorator(component, system, spanContextPropagated);
  }

  static MessagingSpanDecorator create(
      String component, String system, boolean spanContextPropagated, String sendOperationName) {
    return new MessagingSpanDecorator(component, system, spanContextPropagated, sendOperationName);
  }

  MessagingSpanDecorator(String component, String system, boolean spanContextPropagated) {
    this(component, system, spanContextPropagated, "send");
  }

  private MessagingSpanDecorator(
      String component, String system, boolean spanContextPropagated, String sendOperationName) {
    this.component = component;
    this.system = system;
    this.spanContextPropagated = spanContextPropagated;
    this.sendOperationName = sendOperationName;
  }

  public String getSystem() {
    return system;
  }

  public boolean isSpanContextPropagated(Endpoint endpoint) {
    if (!spanContextPropagated) {
      return false;
    }
    return !component.equals("ironmq")
        || Boolean.parseBoolean(
            toQueryParameters(endpoint.getEndpointUri()).get("preserveHeaders"));
  }

  public String getSendOperationName() {
    return sendOperationName;
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
  public void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection) {
    super.pre(attributes, exchange, endpoint, camelDirection);

    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_DESTINATION_NAME, getDestination(exchange, endpoint));
      attributes.put(MESSAGING_MESSAGE_ID, getMessageId(exchange));
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

  @Nullable
  public String getStableDestination(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {
    if (!component.equals("rabbitmq")) {
      return getDestination(exchange, endpoint);
    }

    Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
    boolean outbound = camelDirection == CamelDirection.OUTBOUND;
    boolean bridgeEndpoint =
        outbound && Boolean.parseBoolean(queryParameters.get("bridgeEndpoint"));
    String exchangeName = exchange.getIn().getHeader("rabbitmq.EXCHANGE_NAME", String.class);
    if (exchangeName == null || bridgeEndpoint) {
      String endpointDestination = stripSchemeAndOptions(endpoint);
      int separator = endpointDestination.lastIndexOf('/');
      exchangeName =
          separator == -1 ? endpointDestination : endpointDestination.substring(separator + 1);
    }
    String routingKey = exchange.getIn().getHeader("rabbitmq.ROUTING_KEY", String.class);
    if (routingKey == null || bridgeEndpoint) {
      routingKey = queryParameters.get("routingKey");
    }

    StringBuilder destination = new StringBuilder();
    appendDestinationPart(destination, exchangeName);
    appendDestinationPart(destination, routingKey);
    if (!outbound) {
      String queue = queryParameters.get("queue");
      if (queue != null && !queue.equals(routingKey)) {
        appendDestinationPart(destination, queue);
      }
    }
    if (destination.length() == 0) {
      return outbound ? "amq.default" : null;
    }
    return destination.toString();
  }

  private static void appendDestinationPart(StringBuilder destination, @Nullable String part) {
    if (part == null || part.isEmpty()) {
      return;
    }
    if (destination.length() != 0) {
      destination.append(':');
    }
    destination.append(part);
  }

  @Override
  public SpanKind getInitiatorSpanKind() {
    switch (component) {
      case "aws-sns":
      case "aws-sqs":
        return SpanKind.INTERNAL;
      default:
        return SpanKind.PRODUCER;
    }
  }

  @Override
  public SpanKind getReceiverSpanKind() {
    switch (component) {
      case "aws-sns":
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
  @Nullable
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

  @Nullable
  public String getStableMessageId(Exchange exchange) {
    if (system.equals("jms") || system.equals("amqp")) {
      return (String) exchange.getIn().getHeader("JMSMessageID");
    }
    return getMessageId(exchange);
  }

  @Nullable
  public String getDestinationPartitionId(Exchange exchange) {
    return null;
  }
}
