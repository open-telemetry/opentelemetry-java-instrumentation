/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pubsub;

import com.google.pubsub.v1.PubsubMessage;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.Map;
import javax.annotation.Nullable;

public enum PubSubAttributesMapGetter implements TextMapGetter<PubsubMessage> {
  INSTANCE;

  @Override
  public Iterable<String> keys(PubsubMessage carrier) {
    return null;
  }

  @Nullable
  @Override
  public String get(@Nullable PubsubMessage carrier, String key) {
    if (carrier == null) {
      return null;
    }
    Map<String, String> headers = carrier.getAttributesMap();
    if (headers == null) {
      return null;
    }
    Object obj = headers.get(key);
    return obj == null ? null : obj.toString();
  }
}
