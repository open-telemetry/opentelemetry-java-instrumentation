/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AttributesExtractorUtil {

  /**
   * Sets the {@code value} with the given {@code key} to the {@link AttributesBuilder} if {@code
   * value} is not {@code null}.
   */
  public static <T> void internalSet(
      AttributesBuilder attributes, AttributeKey<T> key, @Nullable T value) {
    if (value != null) {
      attributes.put(key, value);
    }
  }

  private AttributesExtractorUtil() {}
}
