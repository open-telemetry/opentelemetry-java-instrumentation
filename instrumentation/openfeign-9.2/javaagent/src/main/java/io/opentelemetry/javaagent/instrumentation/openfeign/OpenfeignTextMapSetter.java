/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.RequestTemplate;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

enum OpenfeignTextMapSetter implements TextMapSetter<ExecuteAndDecodeRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable ExecuteAndDecodeRequest request, String key, String value) {
    if (request == null) {
      return;
    }
    RequestTemplate template = request.getRequestTemplate();
    template.header(key, value);
  }
}
