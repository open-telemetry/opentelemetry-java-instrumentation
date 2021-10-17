/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import okhttp3.Request;

/** Helper class to inject span context into request headers. */
final class RequestHeaderSetter implements TextMapSetter<Request.Builder> {

  static final RequestHeaderSetter SETTER = new RequestHeaderSetter();

  @Override
  public void set(@Nullable Request.Builder carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.header(key, value);
  }
}
