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

  private static final boolean emitOldJvmSemconv;
  private static final boolean emitStableJvmSemconv;

  static {
    boolean oldJvm = true;
    boolean stableJvm = false;

    String value = ConfigPropertiesUtil.getString("otel.semconv-stability.opt-in");
    if (value != null) {
      Set<String> values = new HashSet<>(asList(value.split(",")));
      if (values.contains("jvm")) {
        oldJvm = false;
        stableJvm = true;
      }
      if (values.contains("jvm/dup")) {
        oldJvm = true;
        stableJvm = true;
      }
    }

    emitOldJvmSemconv = oldJvm;
    emitStableJvmSemconv = stableJvm;
  }

  public static boolean emitOldJvmSemconv() {
    return emitOldJvmSemconv;
  }

  public static boolean emitStableJvmSemconv() {
    return emitStableJvmSemconv;
  }

  private SemconvStability() {}
}
