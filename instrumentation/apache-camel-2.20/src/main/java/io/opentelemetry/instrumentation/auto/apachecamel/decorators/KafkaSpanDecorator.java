/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.trace.Span;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class KafkaSpanDecorator extends MessagingSpanDecorator {

  public static final String KAFKA_PARTITION_TAG = "kafka.partition";
  public static final String KAFKA_PARTITION_KEY_TAG = "kafka.partition.key";
  public static final String KAFKA_KEY_TAG = "kafka.key";
  public static final String KAFKA_OFFSET_TAG = "kafka.offset";

  protected static final String PARTITION_KEY = "kafka.PARTITION_KEY";

  protected static final String PARTITION = "kafka.PARTITION";
  protected static final String KEY = "kafka.KEY";
  protected static final String TOPIC = "kafka.TOPIC";
  protected static final String OFFSET = "kafka.OFFSET";

  public KafkaSpanDecorator() {
    super("kafka");
  }

  @Override
  public String getDestination(Exchange exchange, Endpoint endpoint) {
    String topic = (String) exchange.getIn().getHeader(TOPIC);
    if (topic == null) {
      Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
      topic = queryParameters.get("topic");
    }
    return topic != null ? topic : super.getDestination(exchange, endpoint);
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    super.pre(span, exchange, endpoint);

    String partition = getValue(exchange, PARTITION, Integer.class);
    if (partition != null) {
      span.setAttribute(KAFKA_PARTITION_TAG, partition);
    }

    String partitionKey = (String) exchange.getIn().getHeader(PARTITION_KEY);
    if (partitionKey != null) {
      span.setAttribute(KAFKA_PARTITION_KEY_TAG, partitionKey);
    }

    String key = (String) exchange.getIn().getHeader(KEY);
    if (key != null) {
      span.setAttribute(KAFKA_KEY_TAG, key);
    }

    String offset = getValue(exchange, OFFSET, Long.class);
    if (offset != null) {
      span.setAttribute(KAFKA_OFFSET_TAG, offset);
    }
  }

  /**
   * Extracts header value from the exchange for given header
   *
   * @param exchange the {@link Exchange}
   * @param header the header name
   * @param type the class type of the exchange header
   * @return
   */
  private <T> String getValue(final Exchange exchange, final String header, Class<T> type) {
    T partition = exchange.getIn().getHeader(header, type);
    return partition != null
        ? String.valueOf(partition)
        : exchange.getIn().getHeader(header, String.class);
  }
}
