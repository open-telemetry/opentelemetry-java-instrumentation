/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import javax.annotation.Nullable;

class ExperimentalHelper {

  private static final boolean EXPERIMENTAL_ATTRIBUTES_ENABLED =
      Boolean.getBoolean("otel.instrumentation.lettuce.experimental-span-attributes");

  @Nullable
  static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES_ENABLED ? value : null;
  }

  private ExperimentalHelper() {}
}
