/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

/** Extractor for the actual arguments passed to the parameters of the traced method. */
@FunctionalInterface
public interface MethodArgumentsExtractor<REQUEST> {
  /** Extracts an array of the actual arguments from the {@link REQUEST}. */
  Object[] extract(REQUEST request);
}
