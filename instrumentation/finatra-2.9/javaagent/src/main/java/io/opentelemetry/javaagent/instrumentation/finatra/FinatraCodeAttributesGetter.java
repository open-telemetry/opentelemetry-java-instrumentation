/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class FinatraCodeAttributesGetter implements CodeAttributesGetter<Class<?>> {
  @Nullable
  @Override
  public Class<?> codeClass(Class<?> request) {
    return request;
  }

  @Nullable
  @Override
  public String methodName(Class<?> request) {
    return null;
  }
}
