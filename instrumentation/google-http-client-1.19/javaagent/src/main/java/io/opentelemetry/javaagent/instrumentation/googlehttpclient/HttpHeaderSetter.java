/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

final class HttpHeaderSetter implements TextMapSetter<HttpRequest> {

  @Override
  public void set(@Nullable HttpRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getHeaders().set(key, value);
  }
}
