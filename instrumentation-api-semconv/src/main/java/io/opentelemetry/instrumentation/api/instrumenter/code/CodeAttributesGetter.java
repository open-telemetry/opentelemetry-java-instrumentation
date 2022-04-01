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
  Class<?> codeClass(REQUEST request);

  @Nullable
  String methodName(REQUEST request);
}
