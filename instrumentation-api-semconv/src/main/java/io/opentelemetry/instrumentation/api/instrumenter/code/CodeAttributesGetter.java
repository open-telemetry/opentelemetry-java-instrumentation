/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.code;

import javax.annotation.Nullable;

/**
 * An interface for getting code attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link CodeAttributesExtractor} to obtain the various
 * code attributes in a type-generic way.
 */
public interface CodeAttributesGetter<REQUEST> {

  @Nullable
  default Class<?> getCodeClass(REQUEST request) {
    return codeClass(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getCodeClass(Object)} instead.
   */
  @Deprecated
  @Nullable
  default Class<?> codeClass(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }

  @Nullable
  default String getMethodName(REQUEST request) {
    return methodName(request);
  }

  /**
   * This method is deprecated and will be removed in the subsequent release.
   *
   * @deprecated Use {@link #getMethodName(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String methodName(REQUEST request) {
    throw new UnsupportedOperationException(
        "This method is deprecated and will be removed in the subsequent release.");
  }
}
