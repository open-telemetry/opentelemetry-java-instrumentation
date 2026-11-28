/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTargetBuilder;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;

final class RocketMqServerAttributesHelper {

  private static final VirtualField<DefaultMQProducerImpl, Attributes> PRODUCER_SERVER_ATTRIBUTES =
      VirtualField.find(DefaultMQProducerImpl.class, Attributes.class);

  @SuppressWarnings("deprecation") // Hook registration uses the implementation accessor.
  static void initialize(DefaultMQProducer producer) {
    PRODUCER_SERVER_ATTRIBUTES.set(
        producer.getDefaultMQProducerImpl(), createAttributes(producer.getNamesrvAddr()));
  }

  static Attributes createAttributes(@Nullable String namesrvAddr) {
    if (namesrvAddr == null || namesrvAddr.isEmpty()) {
      return Attributes.empty();
    }

    ServerTargetBuilder builder = ServerTarget.builder().setPortAlwaysInline(true);
    for (String endpoint : namesrvAddr.split(";")) {
      int separator = endpoint.lastIndexOf(':');
      if (separator <= 0) {
        return Attributes.empty();
      }
      int port;
      try {
        port = Integer.parseInt(endpoint.substring(separator + 1));
      } catch (NumberFormatException ignored) {
        return Attributes.empty();
      }
      // The builder treats negative ports as unspecified, but RocketMQ requires a port.
      if (port < 0) {
        return Attributes.empty();
      }
      builder.addEndpoint(endpoint.substring(0, separator), port);
    }

    ServerTarget target = builder.build();
    return target == null ? Attributes.empty() : Attributes.of(SERVER_ADDRESS, target.getAddress());
  }

  static Attributes getProducerAttributes(@Nullable DefaultMQProducerImpl producer) {
    if (producer == null) {
      return Attributes.empty();
    }
    Attributes attributes = PRODUCER_SERVER_ATTRIBUTES.get(producer);
    return attributes == null ? Attributes.empty() : attributes;
  }

  private RocketMqServerAttributesHelper() {}
}
