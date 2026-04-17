/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import jodd.http.HttpRequest;

final class HttpHeaderSetter implements TextMapSetter<HttpRequest> {

  @Override
  public void set(@Nullable HttpRequest carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.headerOverwrite(key, value);
  }
}
