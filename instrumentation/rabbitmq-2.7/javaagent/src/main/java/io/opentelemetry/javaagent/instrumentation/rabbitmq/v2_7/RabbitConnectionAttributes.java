/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import com.rabbitmq.client.Connection;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The virtual host and cluster name of the RabbitMQ connection. Neither is part of the messaging
 * semantic conventions yet, see open-telemetry/semantic-conventions#3997, so both are opt-in.
 */
public final class RabbitConnectionAttributes {

  private static final AttributeKey<String> MESSAGING_RABBITMQ_VHOST_NAME =
      AttributeKey.stringKey("messaging.rabbitmq.vhost.name");
  private static final AttributeKey<String> MESSAGING_RABBITMQ_CLUSTER_NAME =
      AttributeKey.stringKey("messaging.rabbitmq.cluster.name");

  /**
   * The virtual host of a connection. {@link Connection} has never exposed it, so it is read off
   * {@code AMQConnection} and remembered here.
   */
  public static final VirtualField<Connection, String> VIRTUAL_HOST =
      VirtualField.find(Connection.class, String.class);

  static final boolean CAPTURE_VHOST_NAME;
  static final boolean CAPTURE_CLUSTER_NAME;

  static {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "rabbitmq");
    CAPTURE_VHOST_NAME = config.getBoolean("capture_vhost_name/development", false);
    CAPTURE_CLUSTER_NAME = config.getBoolean("capture_cluster_name/development", false);
  }

  static boolean enabled() {
    return CAPTURE_VHOST_NAME || CAPTURE_CLUSTER_NAME;
  }

  static void apply(AttributesBuilder attributes, @Nullable Connection connection) {
    if (connection == null) {
      return;
    }
    if (CAPTURE_VHOST_NAME) {
      String vhost = VIRTUAL_HOST.get(connection);
      if (vhost != null && !vhost.isEmpty()) {
        attributes.put(MESSAGING_RABBITMQ_VHOST_NAME, vhost);
      }
    }
    if (CAPTURE_CLUSTER_NAME) {
      String clusterName = clusterName(connection);
      if (clusterName != null && !clusterName.isEmpty()) {
        attributes.put(MESSAGING_RABBITMQ_CLUSTER_NAME, clusterName);
      }
    }
  }

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

  private RabbitConnectionAttributes() {}
}
