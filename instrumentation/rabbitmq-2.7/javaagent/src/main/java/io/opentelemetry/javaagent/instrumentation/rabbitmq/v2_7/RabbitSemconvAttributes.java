/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import io.opentelemetry.api.common.AttributeKey;

final class RabbitSemconvAttributes {
  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> MESSAGING_SYSTEM = AttributeKey.stringKey("messaging.system");
  // copied from MessagingIncubatingAttributes
  static final AttributeKey<String> MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY =
      AttributeKey.stringKey("messaging.rabbitmq.destination.routing_key");
  // copied from MessagingIncubatingAttributes
  static final AttributeKey<Long> MESSAGING_RABBITMQ_MESSAGE_DELIVERY_TAG =
      AttributeKey.longKey("messaging.rabbitmq.message.delivery_tag");

  private RabbitSemconvAttributes() {}
}
