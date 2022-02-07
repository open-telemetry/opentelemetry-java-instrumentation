/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.context.propagation.TextMapSetter;
import ratpack.api.Nullable;
import ratpack.http.client.RequestSpec;

enum RequestHeaderSetter implements TextMapSetter<RequestSpec> {
  INSTANCE;

  @Override
  public void set(@Nullable RequestSpec carrier, String key, String value) {
    if (carrier != null) {
      carrier.getHeaders().set(key, value);
    }
  }
}
