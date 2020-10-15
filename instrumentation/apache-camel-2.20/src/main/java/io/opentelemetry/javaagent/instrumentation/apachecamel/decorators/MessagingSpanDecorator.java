/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Span.Kind;
import io.opentelemetry.trace.attributes.SemanticAttributes;
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
  public String getOperationName(Exchange exchange, Endpoint endpoint) {

    switch (component) {
      case "mqtt":
        return stripSchemeAndOptions(endpoint);
    }
    return getDestination(exchange, endpoint);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    super.pre(span, exchange, endpoint);

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
        {
          String destination = stripSchemeAndOptions(endpoint);
          if (destination.startsWith("queue:")) {
            destination = destination.substring("queue:".length());
          }
          return destination;
        }
      case "mqtt":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          return (queryParameters.containsKey("subscribeTopicNames")
              ? queryParameters.get("subscribeTopicNames")
              : queryParameters.get("publishTopicName"));
        }
    }
    return stripSchemeAndOptions(endpoint);
  }

  @Override
  public Span.Kind getInitiatorSpanKind() {
    return Kind.PRODUCER;
  }

  @Override
  public Span.Kind getReceiverSpanKind() {
    return Kind.CONSUMER;
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
    }
    return null;
  }
}
