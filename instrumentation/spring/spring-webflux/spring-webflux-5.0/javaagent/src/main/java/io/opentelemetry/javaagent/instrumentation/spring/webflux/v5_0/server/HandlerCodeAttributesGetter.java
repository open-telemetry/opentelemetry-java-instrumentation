/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class HandlerCodeAttributesGetter implements CodeAttributesGetter<Object> {
  @Nullable
  @Override
  public Class<?> getCodeClass(Object handler) {
    return handler.getClass();
  }

  @Nullable
  @Override
  public String getMethodName(Object handler) {
    return "handle";
  }
}
