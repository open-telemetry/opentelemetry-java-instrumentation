/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.khttp;

import io.opentelemetry.context.propagation.TextMapPropagator;
import java.util.HashMap;
import java.util.Map;

public class KHttpHeadersInjectAdapter implements TextMapPropagator.Setter<Map<String, String>> {

  public static Map<String, String> asWritable(Map<String, String> headers) {
    // Kotlin likes to use read-only data structures, so wrap into new writable map
    return new HashMap<>(headers);
  }

  public static final KHttpHeadersInjectAdapter SETTER = new KHttpHeadersInjectAdapter();

  @Override
  public void set(Map<String, String> carrier, String key, String value) {
    carrier.put(key, value);
  }
}
