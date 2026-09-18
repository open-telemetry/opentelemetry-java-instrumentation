/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitSingletons.VIRTUAL_HOST;

import com.rabbitmq.client.Connection;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The virtual host and cluster name of the RabbitMQ connection. Neither is part of the messaging
 * semantic conventions yet, see open-telemetry/semantic-conventions#3997, so both ride the same
 * {@code otel.instrumentation.rabbitmq.experimental-span-attributes} opt-in as the rest of this
 * module's experimental attributes, see {@link
 * RabbitInstrumenterHelper#CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES}.
 */
class RabbitConnectionAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  private static final AttributeKey<String> MESSAGING_RABBITMQ_VHOST_NAME =
      AttributeKey.stringKey("messaging.rabbitmq.vhost.name");
  private static final AttributeKey<String> MESSAGING_RABBITMQ_CLUSTER_NAME =
      AttributeKey.stringKey("messaging.rabbitmq.cluster.name");

  private final Function<REQUEST, Connection> connectionExtractor;

  RabbitConnectionAttributesExtractor(Function<REQUEST, Connection> connectionExtractor) {
    this.connectionExtractor = connectionExtractor;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Connection connection = connectionExtractor.apply(request);
    if (connection == null || !RabbitInstrumenterHelper.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return;
    }
    String vhost = VIRTUAL_HOST.get(connection);
    if (vhost != null && !vhost.isEmpty()) {
      attributes.put(MESSAGING_RABBITMQ_VHOST_NAME, vhost);
    }
    String clusterName = clusterName(connection);
    if (clusterName != null && !clusterName.isEmpty()) {
      attributes.put(MESSAGING_RABBITMQ_CLUSTER_NAME, clusterName);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}

  @Nullable
  private static String clusterName(Connection connection) {
    Map<String, Object> serverProperties = connection.getServerProperties();
    if (serverProperties == null) {
      return null;
    }
    // the broker sends the server properties as an AMQP field table, in which strings are decoded
    // into LongString rather than String
    Object clusterName = serverProperties.get("cluster_name");
    return clusterName == null ? null : clusterName.toString();
  }
}
