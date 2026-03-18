/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.net.HttpURLConnection;
import javax.annotation.Nullable;

final class RequestPropertySetter implements TextMapSetter<HttpURLConnection> {

  @Override
  public void set(@Nullable HttpURLConnection carrier, String key, String value) {
    if (carrier == null) {
      return;
    }
    carrier.setRequestProperty(key, value);
  }
}
