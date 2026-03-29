/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.vertx.core.http.HttpClientRequest;
import javax.annotation.Nullable;

public final class HttpRequestHeaderSetter implements TextMapSetter<HttpClientRequest> {

  @Override
  public void set(@Nullable HttpClientRequest carrier, String key, String value) {
    if (carrier != null) {
      carrier.putHeader(key, value);
    }
  }
}
