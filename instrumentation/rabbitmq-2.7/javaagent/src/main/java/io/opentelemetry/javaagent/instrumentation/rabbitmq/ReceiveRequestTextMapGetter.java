/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

enum ReceiveRequestTextMapGetter implements TextMapGetter<ReceiveRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(ReceiveRequest carrier) {
    return Optional.of(carrier)
        .map(ReceiveRequest::getResponse)
        .map(GetResponse::getProps)
        .map(AMQP.BasicProperties::getHeaders)
        .map(Map::keySet)
        .orElse(Collections.emptySet());
  }

  @Nullable
  @Override
  public String get(@Nullable ReceiveRequest carrier, String key) {
    return Optional.ofNullable(carrier)
        .map(ReceiveRequest::getResponse)
        .map(GetResponse::getProps)
        .map(AMQP.BasicProperties::getHeaders)
        .map(headers -> headers.get(key))
        .map(Object::toString)
        .orElse(null);
  }
}
