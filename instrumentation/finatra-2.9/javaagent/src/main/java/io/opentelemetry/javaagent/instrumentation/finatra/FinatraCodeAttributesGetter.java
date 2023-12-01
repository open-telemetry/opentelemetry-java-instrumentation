/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class FinatraCodeAttributesGetter implements CodeAttributesGetter<Class<?>> {
  @Nullable
  @Override
  public Class<?> getCodeClass(Class<?> request) {
    return request;
  }

  @Nullable
  @Override
  public String getMethodName(Class<?> request) {
    return null;
  }
}
