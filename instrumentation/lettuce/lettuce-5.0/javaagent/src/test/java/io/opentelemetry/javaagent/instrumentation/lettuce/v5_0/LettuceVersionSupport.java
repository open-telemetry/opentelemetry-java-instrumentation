/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;

final class LettuceVersionSupport {

  static boolean configuredTargetsSupported() {
    return RedisURI.class.getClassLoader().getResource("io/lettuce/core/tracing/Tracing.class")
        == null;
  }

  private LettuceVersionSupport() {}
}
