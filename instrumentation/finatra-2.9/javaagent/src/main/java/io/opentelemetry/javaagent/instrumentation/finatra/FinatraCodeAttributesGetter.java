/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class FinatraCodeAttributesGetter implements CodeAttributesGetter<FinatraRequest> {
  @Nullable
  @Override
  public Class<?> getCodeClass(FinatraRequest request) {
    return request.declaringClass();
  }

  @Nullable
  @Override
  public String getMethodName(FinatraRequest request) {
    return request.methodName();
  }
}
