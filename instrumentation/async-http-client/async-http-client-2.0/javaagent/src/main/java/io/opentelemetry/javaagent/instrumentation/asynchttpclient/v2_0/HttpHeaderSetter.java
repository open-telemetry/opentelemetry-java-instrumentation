/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;

final class HttpHeaderSetter implements TextMapSetter<RequestContext> {

  @Override
  public void set(@Nullable RequestContext carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getRequest().getHeaders().set(key, value);
  }
}
