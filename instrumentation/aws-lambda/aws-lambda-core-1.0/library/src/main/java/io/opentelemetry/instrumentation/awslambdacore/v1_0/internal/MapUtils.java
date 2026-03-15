/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

import java.util.Locale;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MapUtils {
  public static Map<String, String> lowercaseMap(Map<String, String> source) {
    return emptyIfNull(source).entrySet().stream()
        .filter(e -> e.getKey() != null)
        .collect(toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
  }

  public static Map<String, String> emptyIfNull(Map<String, String> map) {
    return map == null ? emptyMap() : map;
  }

  private MapUtils() {}
}
