/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.http;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

/**
 * An interface for getting the {@code http.route} attribute.
 *
 * @since 2.0.0
 */
@FunctionalInterface
public interface HttpServerRouteBiGetter<T, U> {

  /**
   * Returns the {@code http.route} attribute extracted from {@code context}, {@code arg1} and
   * {@code arg2}; or {@code null} if it was not found.
   */
  @Nullable
  String get(Context context, T arg1, U arg2);
}
