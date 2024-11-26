/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import ratpack.api.Nullable;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum RequestHeaderSetter implements TextMapSetter<RequestSpec> {
  INSTANCE;

  @Override
  public void set(@Nullable RequestSpec carrier, String key, String value) {
    if (carrier != null) {
      carrier.getHeaders().set(key, value);
    }
  }
}
