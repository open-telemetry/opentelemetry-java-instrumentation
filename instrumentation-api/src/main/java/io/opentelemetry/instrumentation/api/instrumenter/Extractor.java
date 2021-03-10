package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;

public abstract class Extractor<REQUEST, RESPONSE> {
  abstract void onStart(AttributesBuilder attributes, REQUEST request);

  abstract void onEnd(AttributesBuilder attributes, REQUEST request, RESPONSE response);

  protected static <T> void set(AttributesBuilder attributes, AttributeKey<T> key, T value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }
}
