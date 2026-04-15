/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.context.propagation.TextMapSetter;
import javax.annotation.Nullable;
import play.shaded.ahc.org.asynchttpclient.Request;

final class HttpHeaderSetter implements TextMapSetter<Request> {

  @Override
  public void set(@Nullable Request carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.getHeaders().set(key, value);
  }
}
