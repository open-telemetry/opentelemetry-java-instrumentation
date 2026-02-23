/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;

enum DeliveryRequestGetter implements TextMapGetter<DeliveryRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(DeliveryRequest carrier) {
    if (carrier == null) {
      return emptyList();
    }
    Map<String, Object> headers = carrier.getProperties().getHeaders();
    if (headers == null) {
      return emptyList();
    }
    return headers.keySet();
  }

  @Override
  public String get(DeliveryRequest carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Map<String, Object> headers = carrier.getProperties().getHeaders();
    if (headers == null) {
      return null;
    }
    Object obj = headers.get(key);
    return obj == null ? null : obj.toString();
  }
}
