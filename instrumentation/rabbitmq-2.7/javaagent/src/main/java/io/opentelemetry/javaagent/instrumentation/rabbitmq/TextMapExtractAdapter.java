/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Collections;

public class TextMapExtractAdapter implements TextMapGetter<DeliveryRequest> {

  public static final TextMapExtractAdapter GETTER = new TextMapExtractAdapter();

  @Override
  public Iterable<String> keys(DeliveryRequest carrier) {
    return carrier != null
        ? carrier.getProperties().getHeaders().keySet()
        : Collections.emptyList();
  }

  @Override
  public String get(DeliveryRequest carrier, String key) {
    if (carrier != null) {
      Object obj = carrier.getProperties().getHeaders().get(key);
      return obj == null ? null : obj.toString();
    } else {
      return null;
    }
  }
}
