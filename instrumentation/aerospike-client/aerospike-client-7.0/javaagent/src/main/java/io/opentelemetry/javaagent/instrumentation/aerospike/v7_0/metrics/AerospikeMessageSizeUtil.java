/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.javaagent.instrumentation.aerospike.v7_0.AerospikeSemanticAttributes;
import javax.annotation.Nullable;

final class AerospikeMessageSizeUtil {

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

  private AerospikeMessageSizeUtil() {}
}
