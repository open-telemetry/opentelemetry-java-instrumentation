/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import java.lang.reflect.Method;

/** Extractor for the traced {@link Method}. */
@FunctionalInterface
public interface MethodExtractor<REQUEST> {

  /** Extracts the {@link Method} corresponding to the {@link REQUEST}. */
  Method extract(REQUEST request);
}
