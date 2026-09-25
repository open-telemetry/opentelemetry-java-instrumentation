/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.code;

import javax.annotation.Nullable;

/**
 * An interface for getting code attributes.
 *
 * <p>Instrumentation authors implement this interface for their library or framework.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.semconv.code.CodeAttributesGetter}
 *     instead. Will be removed in 3.0.
 */
@Deprecated // to be removed in 3.0
public interface CodeAttributesGetter<REQUEST>
    extends io.opentelemetry.instrumentation.api.semconv.code.CodeAttributesGetter<REQUEST> {

  @Override
  @Nullable
  Class<?> getCodeClass(REQUEST request);

  @Override
  @Nullable
  String getMethodName(REQUEST request);
}
