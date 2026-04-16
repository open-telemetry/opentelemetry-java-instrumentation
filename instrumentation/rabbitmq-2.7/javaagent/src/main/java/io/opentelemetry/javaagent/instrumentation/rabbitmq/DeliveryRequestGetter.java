/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import javax.annotation.Nullable;

final class DeliveryRequestGetter implements TextMapGetter<DeliveryRequest> {

  @Override
  public Iterable<String> keys(DeliveryRequest carrier) {
    Map<String, Object> headers = carrier.getProperties().getHeaders();
    if (headers == null) {
      return emptyList();
    }
    return headers.keySet();
  }

  @Nullable
  @Override
  public String get(@Nullable DeliveryRequest carrier, String key) {
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
