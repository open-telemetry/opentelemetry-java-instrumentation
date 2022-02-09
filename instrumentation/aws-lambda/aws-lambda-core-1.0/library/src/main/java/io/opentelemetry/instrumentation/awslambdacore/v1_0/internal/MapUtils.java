/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class MapUtils {
  public static Map<String, String> lowercaseMap(Map<String, String> source) {
    return emptyIfNull(source).entrySet().stream()
        .filter(e -> e.getKey() != null)
        .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
  }

  public static Map<String, String> emptyIfNull(Map<String, String> map) {
    return map == null ? Collections.emptyMap() : map;
  }

  private MapUtils() {}
}
