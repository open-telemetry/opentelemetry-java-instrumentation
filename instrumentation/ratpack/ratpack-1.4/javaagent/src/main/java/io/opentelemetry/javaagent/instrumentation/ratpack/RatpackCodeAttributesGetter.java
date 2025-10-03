/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;
import ratpack.handling.Handler;

public class RatpackCodeAttributesGetter implements CodeAttributesGetter<Handler> {

  @Nullable
  @Override
  public Class<?> getCodeClass(Handler handler) {
    return handler.getClass();
  }

  @Nullable
  @Override
  public String getMethodName(Handler handler) {
    return "handle";
  }
}
