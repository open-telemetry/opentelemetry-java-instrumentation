/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.pekkoremote.v1_0.internal;

import static java.util.Arrays.asList;

import io.opentelemetry.context.propagation.TextMapGetter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum StringTextMapGetter implements TextMapGetter<String> {
  INSTANCE;

  @Override
  public Iterable<String> keys(String carrier) {
    String[] keys = carrier.split("=[^,]*,?");
    return asList(keys);
  }

  @Nullable
  @Override
  public String get(@Nullable String carrier, String key) {
    if (carrier != null) {
      String[] keyValues = carrier.split(",");
      for (int i = 0; i < keyValues.length; i++) {
        String[] keyValue = keyValues[i].split("=");
        try {
          if (keyValue.length == 2 && urlDecode(keyValue[0]).equals(key)) {
            return urlDecode(keyValue[1]);
          }
        } catch (UnsupportedEncodingException e) {
          return null;
        }
      }
    }
    return null;
  }

  private static String urlDecode(String value) throws UnsupportedEncodingException {
    return URLDecoder.decode(value, "UTF-8");
  }
}
