/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.StatusCode;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface StatusExtractor<REQUEST, RESPONSE> {

  @SuppressWarnings("unchecked")
  static <REQUEST, RESPONSE> StatusExtractor<REQUEST, RESPONSE> getDefault() {
    return (StatusExtractor<REQUEST, RESPONSE>) DefaultStatusExtractor.INSTANCE;
  }

  static <REQUEST, RESPONSE> StatusExtractor<REQUEST, RESPONSE> http(
      HttpAttributesExtractor<REQUEST, RESPONSE> attributesExtractor) {
    return new HttpStatusExtractor<>(attributesExtractor);
  }

  StatusCode extract(REQUEST request, RESPONSE response, @Nullable Throwable error);
}
