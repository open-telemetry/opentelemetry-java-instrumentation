/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SemconvStability {

  private static final boolean emitOldHttpSemconv;
  private static final boolean emitStableHttpSemconv;

  static {
    boolean old = true;
    boolean stable = false;

    String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
    if (value != null) {
      Set<String> values = new HashSet<>(asList(value.split(",")));
      if (values.contains("http")) {
        old = false;
        stable = true;
      }
      // no else -- technically it's possible to set "http,http/dup", in which case we should emit
      // both sets of attributes
      if (values.contains("http/dup")) {
        old = true;
        stable = true;
      }
    }

    emitOldHttpSemconv = old;
    emitStableHttpSemconv = stable;
  }

  public static boolean emitOldHttpSemconv() {
    return emitOldHttpSemconv;
  }

  public static boolean emitStableHttpSemconv() {
    return emitStableHttpSemconv;
  }

  private SemconvStability() {}
}
