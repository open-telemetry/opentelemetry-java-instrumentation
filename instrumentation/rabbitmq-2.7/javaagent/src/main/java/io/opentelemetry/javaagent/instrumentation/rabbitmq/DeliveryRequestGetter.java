/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;
import java.util.Map;

enum DeliveryRequestGetter implements TextMapGetter<DeliveryRequest> {
  INSTANCE;

  @Override
  public Iterable<String> keys(DeliveryRequest carrier) {
    if (carrier != null) {
      Map<String, Object> headers = carrier.getProperties().getHeaders();
      if (headers == null) {
        return Collections.emptyList();
      }
      return headers.keySet();
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public String get(DeliveryRequest carrier, String key) {
    if (carrier != null) {
      Map<String, Object> headers = carrier.getProperties().getHeaders();
      if (headers == null) {
        return null;
      }
      Object obj = headers.get(key);
      return obj == null ? null : obj.toString();
    } else {
      return null;
    }
  }
}
