/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.code;

import javax.annotation.Nullable;

/**
 * An interface for getting source code attributes.
 *
 * <p>Instrumentation authors implement this interface for their library or framework. {@link
 * CodeAttributesExtractor} uses it to obtain code attributes from a request.
 */
public interface CodeAttributesGetter<REQUEST> {

  @Nullable
  Class<?> getCodeClass(REQUEST request);

  @Nullable
  String getMethodName(REQUEST request);
}
