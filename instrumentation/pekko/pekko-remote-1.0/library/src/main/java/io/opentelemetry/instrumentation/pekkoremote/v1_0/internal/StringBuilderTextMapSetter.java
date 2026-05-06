/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.pekkoremote.v1_0.internal;

import io.opentelemetry.context.propagation.TextMapSetter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum StringBuilderTextMapSetter implements TextMapSetter<StringBuilder> {
  INSTANCE;

  @Override
  public void set(@Nullable StringBuilder carrier, String key, String value) {
    if (carrier.length() > 0) {
      carrier.append(',');
    }
    try {
      carrier.append(urlEncode(key));
      carrier.append('=');
      carrier.append(urlEncode(value));
    } catch (UnsupportedEncodingException e) {
      // ignore
    }
  }

  private static String urlEncode(String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, "UTF-8");
  }
}
