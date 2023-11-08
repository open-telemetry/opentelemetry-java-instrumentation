package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import javax.annotation.Nullable;

final class DbMessageSizeUtil {

  @Nullable
  static Long getMessageSize(Attributes... attributesList) {
    return getAttribute(AerospikeSemanticAttributes.AEROSPIKE_TRANSFER_SIZE, attributesList);
  }

  @Nullable
  private static <T> T getAttribute(AttributeKey<T> key, Attributes... attributesList) {
    for (Attributes attributes : attributesList) {
      T value = attributes.get(key);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  private DbMessageSizeUtil() {}
}

