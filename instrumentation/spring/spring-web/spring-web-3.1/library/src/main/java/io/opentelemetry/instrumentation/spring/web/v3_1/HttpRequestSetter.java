/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web.v3_1;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;

enum HttpRequestSetter implements TextMapSetter<HttpRequest> {
  INSTANCE;

  @Override
  public void set(@Nullable HttpRequest httpRequest, String key, String value) {
    if (httpRequest == null) {
      return;
    }
    httpRequest.getHeaders().set(key, value);
  }
}
