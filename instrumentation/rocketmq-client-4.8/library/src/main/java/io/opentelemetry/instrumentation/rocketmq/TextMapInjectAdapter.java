/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmq;

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeader;

public class TextMapInjectAdapter implements TextMapPropagator.Setter<SendMessageRequestHeader> {

  public static final TextMapInjectAdapter SETTER = new TextMapInjectAdapter();

  @Override
  public void set(SendMessageRequestHeader header, String key, String value) {
    StringBuilder properties = new StringBuilder(header.getProperties());
    properties.append(key);
    properties.append('\u0001');
    properties.append(value);
    properties.append('\u0002');
    header.setProperties(properties.toString());
  }
}
